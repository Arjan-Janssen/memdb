include!("checked_wrapper.rs");

const GROW_SIZE: i64 = 10000;

fn grow_vec() {
    let mut grow = vec![1, 2, 3];
    for i in 0..GROW_SIZE {
        grow.push(i);
    }
    memdb_lib::server::send_marker("growing");
}

fn main() {
    let server_handle = checked_run_with_default_address();
    checked_send_marker("begin");
    grow_vec();
    checked_send_marker("end");
    checked_send_terminate();
    checked_join_thread(server_handle);
}
