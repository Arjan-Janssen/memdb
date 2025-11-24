use std::process;

fn growing_vec() {
    let mut growing_vec = vec![1, 2, 3];
    for i in 1..10000 {
        growing_vec.push(i);
    }
    memdb_lib::server::send_marker("on-stack");
}

fn main() {
    let server_thread = memdb_lib::server::run_with_default_address().unwrap_or_else(|error| {
        println!("Unable to run server on default address: {error:?}");
        process::exit(-1);
    });
    memdb_lib::server::send_marker("begin");
    growing_vec();
    memdb_lib::server::send_marker("end");
    memdb_lib::server::send_terminate();
    server_thread.join().unwrap_or_else(|error| {
        println!("Unable to join server thread: {error:?}");
        process::exit(-1);
    });
}
