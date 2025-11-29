include!("checked_wrapper.rs");

const NUM_ITERATIONS: i64 = 25;
const GROW_SIZE_PER_ITERATION: i64 = 10000;

fn growing_vec(iteration: i64) {
    let mut growing_vec = vec![1, 2, 3];
    let grow_size = GROW_SIZE_PER_ITERATION * (iteration + 1);
    for i in 0..grow_size {
        growing_vec.push(i);
    }
    checked_send_marker("growing");
}

fn main() {
    let server_handle = checked_run_with_default_address();
    checked_send_marker("begin");

    for i in 0..NUM_ITERATIONS {
        growing_vec(i);
        checked_send_marker("iteration");
    }
    checked_send_marker("end");
    checked_send_terminate();
    checked_join_thread(server_handle);
}
