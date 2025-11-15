use protobuf::Message;
use std::{
    alloc::{GlobalAlloc, Layout, System},
    time::SystemTime,
    cell::Cell,
    io::prelude::*,
    net::{TcpListener, TcpStream},
    sync::{mpsc::{self, SyncSender, Receiver, channel}, atomic::{AtomicPtr, Ordering}},
    thread, thread::ThreadId, thread::JoinHandle,
    time::Duration,
};
mod generated;

#[derive(Debug)]
pub enum HeapOperationKind {
    Alloc,
    Dealloc,
}

#[derive(Debug)]
pub struct HeapOperation {
    address: usize,
    layout: Layout,
    thread_id: ThreadId,
    kind: HeapOperationKind,
}

pub struct Server {
    max_heap_operations: usize,
    proto: generated::message::HeapOperations,
    stream: TcpStream,
    server_thread_id: thread::ThreadId,
    sender: SyncSender<HeapOperation>,
    receiver: Receiver<HeapOperation>,
    num_heap_operations_sent: usize,
    start_time: SystemTime,
}

pub enum NetworkError {
    ConnectionError,
}

impl Drop for Server {
    fn drop(&mut self) {
        self.flush(true);
        self.stream.shutdown(std::net::Shutdown::Both).expect("Unable to shutdown TCP communication stream");
        println!("Num heap operations sent: {}", self.num_heap_operations_sent);
        println!("Closing server...");
    }
}

impl Server {
    pub fn new(
        max_heap_operations: usize,
    ) -> Result<Server, NetworkError> {
        let (sender, receiver) = mpsc::sync_channel::<HeapOperation>(1 as usize);

        let mut proto = generated::message::HeapOperations::new();
        proto.heap_operations.reserve(max_heap_operations);

        match Self::establish_connection() {
            Ok(stream) => {
                let server_thread_id = thread::current().id();
                let server_start_time = SystemTime::now();
            
                Ok(Self {
                    max_heap_operations,
                    proto,
                    stream,
                    server_thread_id,
                    sender,
                    receiver,
                    num_heap_operations_sent: 0,
                    start_time: server_start_time,
                })
            },
            Err(e) => Err(e),
        }
    }

    pub fn send(&mut self, heap_op : HeapOperation) {
        if thread::current().id() == self.server_thread_id {
            return;
        }

        let result = self.sender.send(heap_op);
        if result.is_err() {
            println!("Send error");
        }
    }

    fn establish_connection() -> Result<TcpStream, NetworkError> {
        let listener = TcpListener::bind("127.0.0.1:8989").unwrap();
        for stream in listener.incoming() {
            return Ok(stream.unwrap());
        }
        Err(NetworkError::ConnectionError)
    }

    fn flush(&mut self, end_of_file: bool) {
        let mut proto_bytes_buffer: Vec<u8> = vec![];
        self.proto.end_of_file = end_of_file;
        let _ = self.proto.write_to_vec(&mut proto_bytes_buffer);
        let result = self.stream.write(&proto_bytes_buffer);
        if result.is_err() {
            println!("Unable to write heap operations to socket stream! Connection lost?");
        }
        
        self.num_heap_operations_sent += self.proto.heap_operations.len();
        self.proto.clear();
        self.stream.flush().expect("Unable to flush heap operation TCP stream");
    }

    fn create_proto_heap_op(duration_since_server_start: Duration, heap_op: HeapOperation) -> generated::message::HeapOperation {
        let mut proto_op = generated::message::HeapOperation::new();                
        proto_op.micros_since_server_start = duration_since_server_start.as_micros().try_into().unwrap();
        proto_op.address = heap_op.address as i64;
        proto_op.size = Some(heap_op.layout.size() as i64);
        proto_op.thread_id = 0;
        proto_op.type_ = ::protobuf::EnumOrUnknown::new(
            match heap_op.kind {
                HeapOperationKind::Alloc => generated::message::heap_operation::Type::TYPE_ALLOC,
                HeapOperationKind::Dealloc => generated::message::heap_operation::Type::TYPE_FREE,
            });

        proto_op
    }

    fn push(&mut self, heap_op: HeapOperation) {
        let duration_since_server_start = SystemTime::now().duration_since(self.start_time).expect("Unable to calculate delta time");
        self.proto.heap_operations.push(Self::create_proto_heap_op(duration_since_server_start, heap_op));

        if self.proto.heap_operations.iter().count() >= self.max_heap_operations {
            self.flush(false);
        }
    }

    fn run(&mut self, terminate_receiver: Receiver<()>) {
        while terminate_receiver.try_recv().is_err() {
            match self.receiver.recv_timeout(Duration::from_millis(50)) {
                Ok(heap_op) => {
                    self.push(heap_op);
                }
                Err(_) => {
                }
            }
        }
    }
}

pub fn run_server(terminate_receiver: Receiver<()>) -> Result<JoinHandle<()>, std::io::Error> {
    let (connection_sender,connection_receiver) = channel();

    let server_thread_id = thread::Builder::new()
        .name("heap-tracker".to_string())
        .spawn(move || {
            println!("Server thread started!");
            let server = Server::new(10);
            match server {
                Ok(mut server) => {
                    SERVER.store(&mut server, Ordering::Release);
                    connection_sender.send(()).expect("Could not send connection established message");
                    server.run(terminate_receiver);
                    SERVER.store(std::ptr::null_mut(), Ordering::Release);
                }
                Err(_) => {
                    println!("Connection error!");
                }
            }
        });

    println!("Waiting for connection...");
    connection_receiver.recv().expect("Could not receive connection established message");
    println!("Connection established!");

    server_thread_id
}

static SERVER: AtomicPtr<Server> =
    AtomicPtr::<Server>::new(std::ptr::null_mut());

struct TrackedAllocator {}

thread_local! {
    static RECURSION_DEPTH: Cell<i32> = const { Cell::new(0) };
}
pub struct ScopedRecursionDepthLimiter {
    recursion_limit: i32,
}

impl ScopedRecursionDepthLimiter {
    fn new(limit: i32) -> Self {
        RECURSION_DEPTH.with(|depth| depth.set(depth.get() + 1));
        Self{recursion_limit: limit}
    }

    fn limit_reached(&self) -> bool {
        let mut hit = false;
        RECURSION_DEPTH.with(|depth| hit = depth.get() >= self.recursion_limit);
        hit
    }
}

impl Drop for ScopedRecursionDepthLimiter {
    fn drop(&mut self) {
        RECURSION_DEPTH.with(|depth| {
            depth.set(depth.get() - 1);
            assert!(depth.get() >= 0);
        });
    }
}

unsafe impl GlobalAlloc for TrackedAllocator {
    unsafe fn alloc(&self, layout: Layout) -> *mut u8 {
        let ptr = unsafe { System.alloc(layout) };
        unsafe {
            self.hook(HeapOperationKind::Alloc, layout, ptr);
        }
        ptr
    }

    unsafe fn dealloc(&self, ptr: *mut u8, layout: Layout) {
        unsafe {
            self.hook(HeapOperationKind::Dealloc, layout, ptr);
        }
        unsafe { System.dealloc(ptr, layout) }
    }    
}

impl TrackedAllocator {
    unsafe fn hook(&self, kind: HeapOperationKind, layout: Layout, ptr: *mut u8) -> () {
        let server_ptr = SERVER.load(Ordering::Acquire);
        if server_ptr.is_null() {
            return
        }

        let recursion_limiter = ScopedRecursionDepthLimiter::new(2);
        if recursion_limiter.limit_reached() {
            return;
        }

        unsafe {
            (*server_ptr).send(HeapOperation{
                address: ptr as usize,
                layout: layout,
                thread_id: thread::current().id(),
                kind: kind,
            });
        }
    }
}

#[global_allocator]
static mut GLOBAL_ALLOCATOR: TrackedAllocator = TrackedAllocator {};

fn main() {
    println!("Heap tracker by Arjan Janssen");

    let (terminate_sender, terminate_receiver) = channel();
    let server_thread = run_server(terminate_receiver);

    let mut growing_vec = vec![1, 2, 3];
    for i in 1..10000 {
        growing_vec.push(i);
    }

    println!("Sending terminate signal to server thread!");
    terminate_sender.send(()).expect("Could not send terminate message to server thread");

    let join_result = server_thread.unwrap().join();
    if join_result.is_err() {
        println!("Error joining thread");
        return;
    }

    println!("Closing...");
}
