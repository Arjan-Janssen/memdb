use memdb_lib;

fn growing_vec() {
    let mut growing_vec = vec![1, 2, 3];
    for i in 1..10000 {
        growing_vec.push(i);
    }
}

fn main() {
    let server_thread = memdb_lib::server::run();
    memdb_lib::server::send_marker("begin");
    growing_vec();
    memdb_lib::server::send_marker("end");

    memdb_lib::server::send_terminate();

    let join_result = server_thread.unwrap().join();
    if join_result.is_err() {
        println!("Error joining thread");
        return;
    }

    println!("Closing...");
}
