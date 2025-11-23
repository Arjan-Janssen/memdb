package com.janssen.memdb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiffTest {
    private fun createMatchedAllocAndDeallocScenario() =
        TrackedHeap
            .Builder()
            .addHeapOperation(HeapOperation.Builder().alloc(5, 2))
            .addHeapOperation(HeapOperation.Builder().dealloc(5))
            .build()

    private fun createMismatchedAllocAndDeallocScenario() =
        TrackedHeap
            .Builder()
            .addHeapOperation(HeapOperation.Builder().alloc(1, 2))
            .addHeapOperation(HeapOperation.Builder().dealloc(2))
            .build()

    @Test
    fun `compute diff with single alloc operation`() {
        val trackedHeap = createMismatchedAllocAndDeallocScenario()
        val diff = Diff.compute(trackedHeap, "0..1")
        assertEquals(1, diff.added.size)
        assertEquals(trackedHeap.heapOperations.first(), diff.added.first())
        assertEquals(0, diff.removed.size)
    }

    @Test
    fun `compute diff with single dealloc operation`() {
        val trackedHeap = createMismatchedAllocAndDeallocScenario()
        val diff = Diff.compute(trackedHeap, "1..2")
        assertEquals(0, diff.added.size)
        assertEquals(1, diff.removed.size)
        assertEquals(trackedHeap.heapOperations.last(), diff.removed.first())
    }

    @Test
    fun `compute diff with mismatched alloc and dealloc operation`() {
        val trackedHeap = createMismatchedAllocAndDeallocScenario()
        val diff = Diff.compute(trackedHeap, "0..2")
        assertEquals(1, diff.added.size)
        assertEquals(trackedHeap.heapOperations.first(), diff.added.first())
        assertEquals(1, diff.removed.size)
        assertEquals(trackedHeap.heapOperations.last(), diff.removed.first())
    }

    @Test
    fun `compute diff with matched alloc and dealloc operation`() {
        val trackedHeap = createMatchedAllocAndDeallocScenario()
        val diff = Diff.compute(trackedHeap, "0..2")
        assertEquals(0, diff.added.size)
        assertEquals(0, diff.removed.size)
    }

    @Test
    fun `compute reversed diff with single dealloc operation`() {
        val trackedHeap = createMismatchedAllocAndDeallocScenario()
        val diff = Diff.compute(trackedHeap, "1..0")
        assertEquals(0, diff.added.size)
        assertEquals(1, diff.removed.size)
        assertEquals(trackedHeap.heapOperations.first(), diff.removed.first())
    }

    @Test
    fun `compute reversed diff with single alloc operation`() {
        val trackedHeap = createMismatchedAllocAndDeallocScenario()
        val diff = Diff.compute(trackedHeap, "2..1")
        assertEquals(1, diff.added.size)
        assertEquals(trackedHeap.heapOperations.last(), diff.added.first())
        assertEquals(0, diff.removed.size)
    }
}
