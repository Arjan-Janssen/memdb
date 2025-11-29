use std::process;

fn main() {
    println!("memdb (c) 2025 by Arjan Janssen");

    let server_thread = memdb_lib::server::run_with_default_address().unwrap_or_else(|error| {
        println!("Unable to run server on default address: {error:?}");
        process::exit(-1);
    });

    if !memdb_lib::server::send_marker("hello") {
        println!("Unable to send begin marker");
        process::exit(-1);
    }

    if !memdb_lib::server::send_terminate() {
        println!("Unable to terminate server");
        process::exit(-1);
    }

    server_thread.join().unwrap_or_else(|error| {
        println!("Unable to join server thread: {error:?}");
        process::exit(-1);
    });
}
