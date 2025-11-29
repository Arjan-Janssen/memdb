use std::process;

fn checked_run_with_default_address() -> std::thread::JoinHandle<()> {
    memdb_lib::server::run_with_default_address().unwrap_or_else(|error| {
        println!("Unable to run server on default address: {error:?}");
        process::exit(-1);
    })
}

fn checked_send_marker(name: &'static str) {
    if !memdb_lib::server::send_marker(name) {
        println!("Unable to send marker {name} to server");
        process::exit(-1);
    }
}

fn checked_send_terminate() {
    if !memdb_lib::server::send_terminate() {
        println!("Unable to send terminate message to server");
        process::exit(-1);
    }
}

fn checked_join_thread(handle: std::thread::JoinHandle<()>) {
    handle.join().unwrap_or_else(|error| {
        println!("Unable to join thread: {error:?}");
        process::exit(-1);
    });
}
