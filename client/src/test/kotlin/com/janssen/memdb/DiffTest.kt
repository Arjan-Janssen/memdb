package com.janssen.memdb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class DiffTest {
    // creates a tracked heap with a single added and a single removed operation
    private fun createTrackedHeap() =
        TrackedHeap(
            listOf(
                HeapOperation(
                    0,
                    HeapOperationKind.Alloc,
                    durationSinceServerStart = 200.toDuration(DurationUnit.MILLISECONDS),
                    address = 2,
                    size = 4,
                    threadId = 5,
                    backtrace = "alloc backtrace",
                ),
                HeapOperation(
                    1,
                    HeapOperationKind.Dealloc,
                    durationSinceServerStart = 400.toDuration(DurationUnit.MILLISECONDS),
                    address = 10,
                    size = 2,
                    threadId = 6,
                    backtrace = "dealloc backtrace",
                ),
            ),
            markers =
                listOf(
                    Marker(0, "begin"),
                    Marker(1, "end"),
                ),
        )

    @Test
    fun `compute diff with single added operation`() {
        val trackedHeap = createTrackedHeap()
        val diff = Diff.compute(trackedHeap, "0..1")
        assertEquals(1, diff.added.size)
        assertEquals(trackedHeap.heapOperations.first(), diff.added.first())
        assertEquals(0, diff.removed.size)
    }

    @Test
    fun `compute diff with single removed operation`() {
        val trackedHeap = createTrackedHeap()
        val diff = Diff.compute(trackedHeap, "1..2")
        assertEquals(0, diff.added.size)
        assertEquals(1, diff.removed.size)
        assertEquals(trackedHeap.heapOperations.last(), diff.removed.first())
    }

    @Test
    fun `compute diff with single added and single removed operation`() {
        val trackedHeap = createTrackedHeap()
        val diff = Diff.compute(trackedHeap, "0..2")
        assertEquals(1, diff.added.size)
        assertEquals(trackedHeap.heapOperations.first(), diff.added.first())
        assertEquals(1, diff.removed.size)
        assertEquals(trackedHeap.heapOperations.last(), diff.removed.first())
    }

    @Test
    fun `compute reversed diff with single removed operation`() {
        val trackedHeap = createTrackedHeap()
        val diff = Diff.compute(trackedHeap, "1..0")
        assertEquals(0, diff.added.size)
        assertEquals(1, diff.removed.size)
        assertEquals(trackedHeap.heapOperations.first(), diff.removed.first())
    }

    @Test
    fun `compute reversed diff with single added operation`() {
        val trackedHeap = createTrackedHeap()
        val diff = Diff.compute(trackedHeap, "2..1")
        assertEquals(1, diff.added.size)
        assertEquals(trackedHeap.heapOperations.last(), diff.added.first())
        assertEquals(0, diff.removed.size)
    }
}
