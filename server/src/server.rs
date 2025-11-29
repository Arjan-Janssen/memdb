use protobuf::Message;
use std::{
    collections::HashMap,
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
    pub size: usize,
    pub thread_id: u64,
    pub kind: HeapOperationKind,
    pub backtrace: String,
}

#[derive(Debug)]
pub enum ServerMessage {
    HeapOperation(HeapOperation),
    Marker(&'static str),
    Terminate,
}

pub struct IpAddress {
    host: &'static str,
    port: u16,
}

pub struct Settings {
    num_heap_operations_per_message: usize,
    store_backtrace: bool,
    ip_address: IpAddress,
}

pub struct Server {
    settings: Settings,
    update: generated::message::Update,
    stream: TcpStream,
    server_thread_id: ThreadId,
    sender: SyncSender<ServerMessage>,
    receiver: Receiver<ServerMessage>,
    num_heap_operations_sent: usize,
    num_bytes_sent: usize,
    start_time: SystemTime,
    terminate: bool,
    marker_counts: HashMap<&'static str, i64>,
}

pub enum NetworkError {
    ConnectionError,
}

impl Drop for Server {
    fn drop(&mut self) {
        if let Err(error) = self.flush() {
            println!("Unable to flush heap operations. Error: {error:?}");
        }
        if let Err(error) = self.stream.shutdown(std::net::Shutdown::Both) {
            println!("Unable to shutdown TCP communication stream. Error: {error:?}");
        }
        println!(
            "Num heap operations sent: {}. Num bytes sent: {}",
            self.num_heap_operations_sent, self.num_bytes_sent
        );
        println!("Closing server...");
    }
}

impl HeapOperation {
    pub fn sentinel() -> HeapOperation {
        HeapOperation {
            address: 0,
            size: 0,
            thread_id: 0,
            kind: HeapOperationKind::Alloc,
            backtrace: String::from(""),
        }
    }
}

impl Server {
    pub fn new(settings: Settings) -> Result<Server, std::io::Error> {
        let (sender, receiver) = mpsc::sync_channel::<ServerMessage>(1 as usize);

        let mut update = generated::message::Update::new();
        update
            .heap_operations
            .reserve(settings.num_heap_operations_per_message);

        match Self::establish_connection(&settings.ip_address) {
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
                    num_bytes_sent: 0,
                    start_time: server_start_time,
                    terminate: false,
                    marker_counts: HashMap::new(),
                })
            }
            Err(e) => Err(e),
        }
    }

    pub fn send(&mut self, message: ServerMessage) {
        if thread::current().id() == self.server_thread_id {
            return;
        }

        if let Err(error) = self.sender.send(message) {
            println!("Unable to send message to server: {error:?}");
        }
    }

    fn establish_connection(ip_address: &IpAddress) -> Result<TcpStream, std::io::Error> {
        let listener = TcpListener::bind(format!("{}:{}", ip_address.host, ip_address.port))?;
        for stream in listener.incoming() {
            return Ok(stream?);
        }
        Err(std::io::Error::other("Unable to establish connection"))
    }

    fn flush(&mut self) -> Result<(), std::io::Error> {
        let mut proto_bytes_buffer: Vec<u8> = vec![];
        let _ = self.update.write_to_vec(&mut proto_bytes_buffer);
        let num_bytes_written = self.stream.write(&proto_bytes_buffer)?;
        self.num_heap_operations_sent += self.update.heap_operations.len();
        self.num_bytes_sent += num_bytes_written;
        self.update.clear();
        self.stream.flush()?;
        Ok(())
    }

    fn create_proto_heap_operation(
        duration_since_server_start: Duration,
        heap_operation: HeapOperation,
        store_backtrace: bool,
    ) -> generated::message::HeapOperation {
        let mut proto_operation = generated::message::HeapOperation::new();
        proto_operation.micros_since_server_start =
            duration_since_server_start.as_micros().try_into().unwrap();
        proto_operation.address = heap_operation.address as i64;
        proto_operation.thread_id = heap_operation.thread_id;
        proto_operation.size = Some(heap_operation.size as i64);
        proto_operation.kind = ::protobuf::EnumOrUnknown::new(match heap_operation.kind {
            HeapOperationKind::Alloc => generated::message::heap_operation::Kind::Alloc,
            HeapOperationKind::Dealloc => generated::message::heap_operation::Kind::Dealloc,
        });
        if store_backtrace {
            proto_operation.backtrace = heap_operation.backtrace;
        }

        proto_operation
    }

    fn push_heap_operation(&mut self, heap_op: HeapOperation) -> Result<(), std::io::Error> {
        let duration_since_server_start = match SystemTime::now().duration_since(self.start_time) {
            Ok(duration) => duration,
            Err(error) => {
                println!("Unable to get system time: {error:?}. Using 0 milliseconds instead.");
                Duration::from_millis(0)
            }
        };
        self.update
            .heap_operations
            .push(Self::create_proto_heap_operation(
                duration_since_server_start,
                heap_op,
                self.settings.store_backtrace,
            ));

        if self.update.heap_operations.iter().count()
            >= self.settings.num_heap_operations_per_message
        {
            self.flush()?;
        }
        Ok(())
    }

    fn push_marker(&mut self, name: &'static str) {
        let mut proto_marker = generated::message::Marker::new();
        proto_marker.name = String::from(name);
        if self.marker_counts.contains_key(name) {}

        let marker_entry = self.marker_counts.entry(name).or_insert(0);
        proto_marker.index = *marker_entry;
        *marker_entry += 1;
        proto_marker.first_operation_seq_no =
            (self.num_heap_operations_sent + self.update.heap_operations.iter().count()) as i64;
        self.update.markers.push(proto_marker);
    }

    fn process(&mut self, message: ServerMessage) -> Result<(), std::io::Error> {
        match message {
            ServerMessage::HeapOperation(heap_op) => self.push_heap_operation(heap_op),
            ServerMessage::Marker(name) => Ok(self.push_marker(name)),
            ServerMessage::Terminate => {
                self.terminate = true;
                self.push_heap_operation(HeapOperation::sentinel())
            }
        }
    }

    fn run(&mut self) {
        while !self.terminate {
            match self.receiver.recv() {
                Ok(server_message) => {
                    if let Err(error) = self.process(server_message) {
                        println!("Unable to process server message. Error: {error:?}",);
                    }
                }
                Err(error) => {
                    println!("Unable to receive message from receiver channel: {error:?}");
                }
            }
        }
    }
}

pub fn run(server_ip_address: IpAddress) -> Result<JoinHandle<()>, std::io::Error> {
    let (connection_sender, connection_receiver) = channel();

    let server_thread_id = thread::Builder::new()
        .name("memdb-server".to_string())
        .spawn(move || {
            println!("Server thread started!");
            let server = Server::new(Settings {
                num_heap_operations_per_message: 64,
                store_backtrace: true,
                ip_address: server_ip_address,
            });
            match server {
                Ok(mut server) => {
                    SERVER.store(&mut server, Ordering::Release);
                    connection_sender.send(()).unwrap_or_else(|error| {
                        println!("Could not send connection established message: {error:?}");
                    });
                    server.run();
                    SERVER.store(std::ptr::null_mut(), Ordering::Release);
                }
                Err(error) => {
                    println!("Server error: {error:?}");
                }
            }
        });

    println!("Waiting for connection...");
    if let Err(_) = connection_receiver.recv() {
        return Err(std::io::Error::other(
            "Unable to receive from server connection channel",
        ));
    }
    println!("Server connection established!");

    server_thread_id
}

/// Creates a server thread at the default address localhost:8989. The function
/// returns a join handle if it succeeds. The instrumented application should join
/// with this thread after calling send_terminate, when it terminates.
/// This function can return an std::io::Error when it fails.
pub fn run_with_default_address() -> Result<JoinHandle<()>, std::io::Error> {
    run(IpAddress {
        host: "127.0.0.1",
        port: 8989,
    })
}

pub static SERVER: AtomicPtr<Server> = AtomicPtr::<Server>::new(std::ptr::null_mut());

fn send_server_message(message: ServerMessage) -> bool {
    let server_ptr = SERVER.load(Ordering::Acquire);
    if server_ptr.is_null() {
        return false;
    }

    unsafe {
        (*server_ptr).send(message);
    }

    true
}

/// Sends a marker with the specified name to the server. When multiple markers with the same name
/// are sent, the server will automatically assign increasing indices so that each marker can be
/// uniquely identified.
/// This function is thread-safe and can be called from any thread.
pub fn send_marker(name: &'static str) -> bool {
    send_server_message(ServerMessage::Marker(name))
}

/// Sends a heap operation to the server.
/// This function is thread-safe and can be called from any thread.
pub fn send_heap_operation(heap_operation: HeapOperation) -> bool {
    send_server_message(ServerMessage::HeapOperation(heap_operation))
}

/// Terminates the server thread. This function should be called when the instrumented application
/// terminates. The instrumented application should call join on the server thread after calling
/// this function.
/// This function is thread-safe and can be called from any thread.
pub fn send_terminate() -> bool {
    send_server_message(ServerMessage::Terminate)
}
