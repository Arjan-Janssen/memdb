use std::process;

fn main() {
    println!("memdb (c) 2025 by Arjan Janssen");

    let server_thread = memdb_lib::server::run_with_default_address().unwrap_or_else(|error| {
        println!("Unable to run server on default address: {error:?}");
        process::exit(1);
    });

    memdb_lib::server::send_marker("hello");
    let heap_op = memdb_lib::server::HeapOperation {
        address: 1,
        size: 4,
        thread_id: 0,
        kind: memdb_lib::server::HeapOperationKind::Alloc,
        backtrace: String::from("backtrace"),
    };
    memdb_lib::server::send_heap_operation(heap_op);
    memdb_lib::server::send_terminate();
    server_thread.join().unwrap_or_else(|error| {
        println!("Unable to join server thread: {error:?}");
        process::exit(1);
    });
}
