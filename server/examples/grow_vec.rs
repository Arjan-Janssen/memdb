use std::process;

const GROW_SIZE: i64 = 10000;

fn grow_vec() {
    let mut grow = vec![1, 2, 3];
    for i in 0..GROW_SIZE {
        grow.push(i);
    }
    memdb_lib::server::send_marker("growing");
}

fn main() {
    let server_thread = memdb_lib::server::run_with_default_address().unwrap_or_else(|error| {
        println!("Unable to run server on default address: {error:?}");
        process::exit(-1);
    });
    memdb_lib::server::send_marker("begin");
    grow_vec();
    memdb_lib::server::send_marker("end");
    memdb_lib::server::send_terminate();
    server_thread.join().unwrap_or_else(|error| {
        println!("Unable to join server thread: {error:?}");
        process::exit(-1);
    });
}
