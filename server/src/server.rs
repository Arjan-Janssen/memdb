use protobuf::Message;
use std::{
    alloc::Layout,
    io::prelude::*,
    net::{TcpListener, TcpStream},
    sync::{
        atomic::{AtomicPtr, Ordering},
        mpsc::{self, Receiver, SyncSender, channel},
    },
    thread::{self, JoinHandle, ThreadId},
    time::{Duration, SystemTime},
};
mod generated;

#[derive(Debug)]
pub enum HeapOperationKind {
    Alloc,
    Dealloc,
}

#[derive(Debug)]
pub struct HeapOperation {
    pub address: usize,
    pub layout: Layout,
    pub thread_id: u64,
    pub kind: HeapOperationKind,
    pub backtrace: String,
}

pub enum ServerMessage {
    HeapOp(HeapOperation),
    Marker(&'static str),
    Terminate,
}

pub struct Settings {
    num_heap_operations_per_message: usize,
    store_backtrace: bool,
}

pub struct Server {
    settings: Settings,
    update: generated::message::Update,
    stream: TcpStream,
    server_thread_id: ThreadId,
    sender: SyncSender<ServerMessage>,
    receiver: Receiver<ServerMessage>,
    num_heap_operations_sent: usize,
    start_time: SystemTime,
    terminate: bool,
}

pub enum NetworkError {
    ConnectionError,
}

impl Drop for Server {
    fn drop(&mut self) {
        self.flush(true);
        self.stream
            .shutdown(std::net::Shutdown::Both)
            .expect("Unable to shutdown TCP communication stream");
        println!(
            "Num heap operations sent: {}",
            self.num_heap_operations_sent
        );
        println!("Closing server...");
    }
}

impl Server {
    pub fn new(settings: Settings) -> Result<Server, NetworkError> {
        let (sender, receiver) = mpsc::sync_channel::<ServerMessage>(1 as usize);

        let mut update = generated::message::Update::new();
        update
            .heap_operations
            .reserve(settings.num_heap_operations_per_message);

        match Self::establish_connection() {
            Ok(stream) => {
                let server_thread_id = thread::current().id();
                let server_start_time = SystemTime::now();

                Ok(Self {
                    settings,
                    update,
                    stream,
                    server_thread_id,
                    sender,
                    receiver,
                    num_heap_operations_sent: 0,
                    start_time: server_start_time,
                    terminate: false,
                })
            }
            Err(e) => Err(e),
        }
    }

    pub fn send(&mut self, message: ServerMessage) {
        if thread::current().id() == self.server_thread_id {
            return;
        }

        let result = self.sender.send(message);
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
        self.update.end_of_file = end_of_file;
        let _ = self.update.write_to_vec(&mut proto_bytes_buffer);
        let result = self.stream.write(&proto_bytes_buffer);
        if result.is_err() {
            println!("Unable to write heap operations to socket stream! Connection lost?");
        }

        self.num_heap_operations_sent += self.update.heap_operations.len();
        self.update.clear();
        self.stream
            .flush()
            .expect("Unable to flush heap operation TCP stream");
    }

    fn create_proto_heap_op(
        duration_since_server_start: Duration,
        heap_op: HeapOperation,
        store_backtrace: bool,
    ) -> generated::message::HeapOperation {
        let mut proto_op = generated::message::HeapOperation::new();
        proto_op.micros_since_server_start =
            duration_since_server_start.as_micros().try_into().unwrap();
        proto_op.address = heap_op.address as i64;
        proto_op.size = Some(heap_op.layout.size() as i64);
        proto_op.thread_id = heap_op.thread_id;
        proto_op.kind = ::protobuf::EnumOrUnknown::new(match heap_op.kind {
            HeapOperationKind::Alloc => generated::message::heap_operation::Kind::ALLOC,
            HeapOperationKind::Dealloc => generated::message::heap_operation::Kind::DEALLOC,
        });
        if store_backtrace {
            proto_op.backtrace = heap_op.backtrace;
        }

        proto_op
    }

    fn push_heap_op(&mut self, heap_op: HeapOperation) {
        let duration_since_server_start = SystemTime::now()
            .duration_since(self.start_time)
            .expect("Unable to calculate delta time");
        self.update.heap_operations.push(Self::create_proto_heap_op(
            duration_since_server_start,
            heap_op,
            self.settings.store_backtrace,
        ));

        if self.update.heap_operations.iter().count()
            >= self.settings.num_heap_operations_per_message
        {
            self.flush(false);
        }
    }

    fn push_marker(&mut self, name: &'static str) {
        let mut marker = generated::message::Marker::new();
        marker.name = String::from(name);
        marker.first_operation_seq_no =
            (self.num_heap_operations_sent + self.update.heap_operations.iter().count()) as i64;
        self.update.markers.push(marker);
    }

    fn process(&mut self, message: ServerMessage) {
        match message {
            ServerMessage::HeapOp(heap_op) => self.push_heap_op(heap_op),
            ServerMessage::Marker(name) => self.push_marker(name),
            ServerMessage::Terminate => self.terminate = true,
        }
    }

    fn run(&mut self) {
        while !self.terminate {
            match self.receiver.recv() {
                Ok(server_message) => {
                    self.process(server_message);
                }
                Err(_) => {}
            }
        }
    }
}

pub fn run() -> Result<JoinHandle<()>, std::io::Error> {
    let (connection_sender, connection_receiver) = channel();

    let server_thread_id = thread::Builder::new()
        .name("heap-tracker".to_string())
        .spawn(move || {
            println!("Server thread started!");
            let server = Server::new(Settings {
                num_heap_operations_per_message: 64,
                store_backtrace: true,
            });
            match server {
                Ok(mut server) => {
                    SERVER.store(&mut server, Ordering::Release);
                    connection_sender
                        .send(())
                        .expect("Could not send connection established message");
                    server.run();
                    SERVER.store(std::ptr::null_mut(), Ordering::Release);
                }
                Err(_) => {
                    println!("Connection error!");
                }
            }
        });

    println!("Waiting for connection...");
    connection_receiver
        .recv()
        .expect("Could not receive connection established message");
    println!("Connection established!");

    server_thread_id
}

pub static SERVER: AtomicPtr<Server> = AtomicPtr::<Server>::new(std::ptr::null_mut());

fn send_server_message(message: ServerMessage) {
    let server_ptr = SERVER.load(Ordering::Acquire);
    if server_ptr.is_null() {
        return;
    }

    unsafe {
        (*server_ptr).send(message);
    }
}

pub fn send_marker(name: &'static str) {
    send_server_message(ServerMessage::Marker(name));
}

pub fn send_terminate() {
    send_server_message(ServerMessage::Terminate);
}
