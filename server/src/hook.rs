use crate::server::{HeapOperation, HeapOperationKind, SERVER, ServerMessage};
use std::{
    alloc::{GlobalAlloc, Layout, System},
    cell::Cell,
    sync::atomic::Ordering,
};

struct TrackedAllocator {}

thread_local! {
    static RECURSION_DEPTH: Cell<i32> = const { Cell::new(0) };
}
pub struct ScopedRecursionDepthLimiter {
    recursion_limit: i32,
}

impl ScopedRecursionDepthLimiter {
    fn new(limit: i32) -> Self {
        RECURSION_DEPTH.with(|depth| depth.set(depth.get() + 1));
        Self {
            recursion_limit: limit,
        }
    }

    fn limit_reached(&self) -> bool {
        let mut hit = false;
        RECURSION_DEPTH.with(|depth| hit = depth.get() >= self.recursion_limit);
        hit
    }
}

impl Drop for ScopedRecursionDepthLimiter {
    fn drop(&mut self) {
        RECURSION_DEPTH.with(|depth| {
            depth.set(depth.get() - 1);
            assert!(depth.get() >= 0);
        });
    }
}

unsafe impl GlobalAlloc for TrackedAllocator {
    unsafe fn alloc(&self, layout: Layout) -> *mut u8 {
        let ptr = unsafe { System.alloc(layout) };
        unsafe {
            self.hook(HeapOperationKind::Alloc, layout, ptr);
        }
        ptr
    }

    unsafe fn dealloc(&self, ptr: *mut u8, layout: Layout) {
        unsafe {
            self.hook(HeapOperationKind::Dealloc, layout, ptr);
        }
        unsafe { System.dealloc(ptr, layout) }
    }
}

impl TrackedAllocator {
    unsafe fn hook(&self, kind: HeapOperationKind, layout: Layout, ptr: *mut u8) -> () {
        let server_ptr = SERVER.load(Ordering::Acquire);
        if server_ptr.is_null() {
            return;
        }

        let recursion_limiter = ScopedRecursionDepthLimiter::new(2);
        if recursion_limiter.limit_reached() {
            return;
        }

        unsafe {
            let backtrace = backtrace::Backtrace::new();
            (*server_ptr).send(ServerMessage::HeapOp(HeapOperation {
                address: ptr as usize,
                layout: layout,
                thread_id: gettid::gettid(),
                kind: kind,
                backtrace: format!("{backtrace:?}"),
            }));
        }
    }
}

#[global_allocator]
static mut GLOBAL_ALLOCATOR: TrackedAllocator = TrackedAllocator {};
