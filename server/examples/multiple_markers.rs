use std::process;

const NUM_ITERATIONS: i64 = 25;
const GROW_SIZE_PER_ITERATION: i64 = 10000;

fn growing_vec(iteration: i64) {
    let mut growing_vec = vec![1, 2, 3];
    let grow_size = GROW_SIZE_PER_ITERATION * (iteration + 1);
    for i in 0..grow_size {
        growing_vec.push(i);
    }
    memdb_lib::server::send_marker("growing");
}

fn main() {
    let server_thread = memdb_lib::server::run_with_default_address().unwrap_or_else(|error| {
        println!("Unable to run server on default address: {error:?}");
        process::exit(-1);
    });
    memdb_lib::server::send_marker("begin");

    for i in 0..NUM_ITERATIONS {
        growing_vec(i);
        memdb_lib::server::send_marker("iteration");
    }
    memdb_lib::server::send_marker("end");
    memdb_lib::server::send_terminate();
    server_thread.join().unwrap_or_else(|error| {
        println!("Unable to join server thread: {error:?}");
        process::exit(-1);
    });
}
