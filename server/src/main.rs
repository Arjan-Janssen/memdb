use memdb_lib;

fn growing_vec() {
    let mut growing_vec = vec![1, 2, 3];
    for i in 1..10000 {
        growing_vec.push(i);
    }
}

fn main() {
    println!("memdb (c) 2025 by Arjan Janssen");

    let server_thread = memdb_lib::server::run().expect("Unable to run server");
    memdb_lib::server::send_marker("begin");
    growing_vec();
    memdb_lib::server::send_marker("end");
    memdb_lib::server::send_terminate();
    if server_thread.join().is_err() {
        println!("Unable to join server thread");
    }
}
