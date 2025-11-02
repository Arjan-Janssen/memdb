use protobuf::Message;
use backtrace;
use std::{
    alloc::{GlobalAlloc, Layout, System},
    cell::Cell,
    io::prelude::*,
    net::{TcpListener, TcpStream},
    sync::{mpsc::{self, SyncSender, Receiver}, atomic::{AtomicPtr, Ordering}},
    thread, thread::JoinHandle,
    time::Duration,
};
mod generated;

#[derive(Debug)]
pub enum HeapOperation {
    Alloc {
        address: usize,
        layout: Layout,
        thread_id: thread::ThreadId,
    },
    Dealloc {
        address: usize,
        layout: Layout,
        thread_id: thread::ThreadId,
    },
}

pub struct Server {
    max_heap_operations: usize,
    proto: generated::message::HeapOperations,
    stream: TcpStream,
    server_thread_id: thread::ThreadId,
    sender: SyncSender<HeapOperation>,
    receiver: Receiver<HeapOperation>,
}

pub enum NetworkError {
    ConnectionError,
}

impl Server {
    pub fn new(
        max_heap_operations: usize,
    ) -> Result<Server, NetworkError> {
        let (mut sender, receiver) = mpsc::sync_channel::<HeapOperation>(1 as usize);

        let mut proto = generated::message::HeapOperations::new();
        proto.heap_operations.reserve(max_heap_operations);

        let server_thread_id = thread::current().id();
        match Self::establish_connection() {
            Ok(stream) => Ok(Self {
                max_heap_operations,
                proto,
                stream,
                server_thread_id,
                sender,
                receiver,
            }),
            Err(e) => Err(e),
        }
    }

    fn establish_connection() -> Result<TcpStream, NetworkError> {
        let listener = TcpListener::bind("127.0.0.1:8989").unwrap();

        for stream in listener.incoming() {
            return Ok(stream.unwrap());
        }
        Err(NetworkError::ConnectionError)
    }

    fn flush_if_needed(&mut self) {
        //if self.proto.heap_operations.iter().count() >= self.max_heap_operations {
        self.flush();
        //}
    }

    pub fn flush(&mut self) {
        println!("Flush");

        let mut v: Vec<u8> = vec![];
        let _ = self.proto.write_to_vec(&mut v);
        let result = self.stream.write(&v);
        if result.is_err() {
            println!("Unable to write to stream!");
        }
        self.proto.clear();
    }

    fn send(&mut self, heap_op : HeapOperation) {
        if thread::current().id() == self.server_thread_id {
            return;
        }

        let result = self.sender.send(heap_op);
        if result.is_err() {
            println!("Send error");
        }
    }

    fn push(&mut self, heap_op: HeapOperation) {
        match heap_op {
            HeapOperation::Alloc {
                address,
                layout,
                thread_id,
            } => {
                let mut proto_op = generated::message::HeapOperation::new();
                proto_op.type_ = ::protobuf::EnumOrUnknown::new(
                    generated::message::heap_operation::Type::TYPE_ALLOC,
                );
                proto_op.address = address as i64;
                proto_op.size = Some(layout.size() as i64);
                proto_op.thread_id = 0;
                self.proto.heap_operations.push(proto_op);
            }
            HeapOperation::Dealloc {
                address,
                layout,
                thread_id,
            } => {
                let mut proto_op = generated::message::HeapOperation::new();
                proto_op.type_ = ::protobuf::EnumOrUnknown::new(
                    generated::message::heap_operation::Type::TYPE_FREE,
                );
                proto_op.address = address as i64;
                proto_op.size = Some(layout.size() as i64);
                proto_op.thread_id = 0;
                self.proto.heap_operations.push(proto_op);
            }
        }

        self.flush_if_needed();
    }

    fn run(&mut self) {
        loop {
            match self.receiver.recv() {
                Ok(heap_op) => {
                    println!("Push operation!");
                    self.push(heap_op);
                }

                Err(e) => {
                    println!("Recv Error!!!")
                }
            }
        }
    }
}

pub fn run_server() -> Result<JoinHandle<()>, std::io::Error> {
    thread::Builder::new()
        .name("heap-tracker".to_string())
        .spawn(move || {
            println!("Running server");
            let mut server = Server::new(2);
    
            match server {
                Ok(mut server) => {
                    SERVER.store(&mut server, Ordering::Release);

                    println!("Connection established!");
                    server.run();
                }
                Err(_) => {
                    println!("Connection error!");
                }
            }
        })
}

static SERVER: AtomicPtr<Server> =
    AtomicPtr::<Server>::new(std::ptr::null_mut());

struct TrackedAllocator {}

//impl TrackedAllocator {
//}

thread_local! {
    static ALLOC_COUNTER: Cell<i32> = const { Cell::new(0) };
    static DEALLOC_COUNTER: Cell<i32> = const { Cell::new(0) };
}

unsafe impl GlobalAlloc for TrackedAllocator {
    unsafe fn alloc(&self, layout: Layout) -> *mut u8 {
        let ptr = unsafe { System.alloc(layout) };
        let server_ptr = SERVER.load(Ordering::Acquire);
        if !server_ptr.is_null() {
            let mut count = 0;
            ALLOC_COUNTER.with(|c| count = c.get());
            if count > 0 {
                return ptr;
            }
           
            ALLOC_COUNTER.with(|c| c.set(c.get() + 1));
            unsafe {
                (*server_ptr).send(HeapOperation::Alloc {
                    address: ptr as usize,
                    layout: layout,
                    thread_id: thread::current().id(),
                });
            }
            ALLOC_COUNTER.with(|c| c.set(c.get() - 1));            
        }

        ptr
    }

    unsafe fn dealloc(&self, ptr: *mut u8, layout: Layout) {
        let server_ptr = SERVER.load(Ordering::Acquire);
        let mut count = 0;
        DEALLOC_COUNTER.with(|c| count = c.get());

        if !server_ptr.is_null() {
            let mut count = 0;
            DEALLOC_COUNTER.with(|c| count = c.get());
            // skip recursive allocations
            if count > 0 {
                return;
            }

            DEALLOC_COUNTER.with(|c| c.set(c.get() + 1));
            unsafe {
                (*server_ptr).send(HeapOperation::Dealloc {
                    address: ptr as usize,
                    layout: layout,
                    thread_id: thread::current().id(),
                });
            }            
            DEALLOC_COUNTER.with(|c| c.set(c.get() - 1));            
        }

        unsafe { System.dealloc(ptr, layout) }
    }
}

#[global_allocator]
static mut GLOBAL_ALLOCATOR: TrackedAllocator = TrackedAllocator {};

fn main() {
    println!("Heap tracker by Arjan Janssen");

    let server_thread = run_server();

    let mut v = vec![1, 2, 3];
    while true {
        println!("Push item...");
        thread::sleep(Duration::from_secs(1));
        v.push(3);
    }

    let join_result = server_thread.unwrap().join();
    if join_result.is_err() {
        println!("Error joining thread");
        return;
    }

    println!("Terminating!");
}
