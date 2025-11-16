mod hook;
mod server;

fn main() {
    println!("Heap tracker (c) 2025 by Arjan Janssen");

    let server_thread = server::run();
    server::send_marker("before");

    let mut growing_vec = vec![1, 2, 3];
    for i in 1..10000 {
        growing_vec.push(i);
    }

    server::send_marker("after");

    println!("Sending terminate signal to server thread!");
    server::send_terminate();

    let join_result = server_thread.unwrap().join();
    if join_result.is_err() {
        println!("Error joining thread");
        return;
    }

    println!("Closing...");
}
