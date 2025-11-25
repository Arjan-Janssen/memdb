use std::process;

fn growing_vec(iteration: i64) {
    let mut growing_vec = vec![1, 2, 3];
    for i in 1..10000 {
        growing_vec.push(i);
    }
    memdb_lib::server::send_marker_indexed("in-scope", iteration);
}

fn main() {
    let server_thread = memdb_lib::server::run_with_default_address().unwrap_or_else(|error| {
        println!("Unable to run server on default address: {error:?}");
        process::exit(-1);
    });
    memdb_lib::server::send_marker("begin");
    let num_iterations = 100i64;
    for i in 0..num_iterations {
        growing_vec(i);
        memdb_lib::server::send_marker_indexed("iteration", i);
    }
    memdb_lib::server::send_marker("end");
    memdb_lib::server::send_terminate();
    server_thread.join().unwrap_or_else(|error| {
        println!("Unable to join server thread: {error:?}");
        process::exit(-1);
    });
}
