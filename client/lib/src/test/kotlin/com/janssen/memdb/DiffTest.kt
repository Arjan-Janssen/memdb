package com.janssen.memdb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiffTest {
    private fun createMatchedAllocAndDeallocScenario() =
        TrackedHeap
            .Builder()
            .addHeapOperation(HeapOperation.Builder().alloc(5, 2))
            .addHeapOperation(HeapOperation.Builder().dealloc(5).size(2))
            .addMarker(Marker(0, "before"))
            .addMarker(Marker(2, "after"))
            .build()

    private fun createUnmatchedAllocAndDeallocScenario() =
        TrackedHeap
            .Builder()
            .addHeapOperation(HeapOperation.Builder().alloc(1, 2))
            .addHeapOperation(HeapOperation.Builder().dealloc(2).size(1))
            .build()

    @Test
    fun `compute diff with alloc`() {
        val trackedHeap = createUnmatchedAllocAndDeallocScenario()
        val diff = Diff.compute(trackedHeap, "0..0")
        assertEquals(1, diff.added.size)
        assertEquals(trackedHeap.heapOperations.first(), diff.added.first())
        assertEquals(0, diff.removed.size)
    }

    @Test
    fun `compute diff with dealloc`() {
        val trackedHeap = createUnmatchedAllocAndDeallocScenario()
        val diff = Diff.compute(trackedHeap, "1..1")
        assertEquals(0, diff.added.size)
        assertEquals(1, diff.removed.size)
        assertEquals(trackedHeap.heapOperations[1], diff.removed.first())
    }

    @Test
    fun `compute diff with mismatched alloc-dealloc pair`() {
        val trackedHeap = createUnmatchedAllocAndDeallocScenario()
        val diff = Diff.compute(trackedHeap, "0..1")
        assertEquals(1, diff.added.size)
        assertEquals(trackedHeap.heapOperations.first(), diff.added.first())
        assertEquals(1, diff.removed.size)
        assertEquals(trackedHeap.heapOperations[1], diff.removed.first())
    }

    @Test
    fun `compute diff with matched alloc-dealloc pair`() {
        val trackedHeap = createMatchedAllocAndDeallocScenario()
        val diff = Diff.compute(trackedHeap, "0..1")
        assertEquals(0, diff.added.size)
        assertEquals(0, diff.removed.size)
    }

    @Test
    fun `compute diff using markers`() {
        val trackedHeap = createMatchedAllocAndDeallocScenario()
        val diff = Diff.compute(trackedHeap, "before..after")
        assertEquals(0, diff.added.size)
        assertEquals(0, diff.removed.size)
    }

    @Test
    fun `compute reversed diff with mismatched alloc-dealloc pair`() {
        val trackedHeap = createUnmatchedAllocAndDeallocScenario()
        val diff = Diff.compute(trackedHeap, "1..0")
        assertEquals(1, diff.added.size)
        assertEquals(1, diff.removed.size)
    }

    @Test
    fun `compute reversed diff with matched alloc-dealloc pair`() {
        val trackedHeap = createMatchedAllocAndDeallocScenario()
        val diff = Diff.compute(trackedHeap, "1..0")
        assertEquals(0, diff.added.size)
        assertEquals(0, diff.removed.size)
    }

    @Test
    @Suppress("MaxLineLength")
    fun `toString prints difference with single allocation`() {
        val trackedHeap = createMatchedAllocAndDeallocScenario()
        val diff = Diff.compute(trackedHeap, "0..0")
        val expectedString =
"""${DiffColor.ADD.color.code}+ alloc[seq no: 0, duration: 0s, address: 00000005, size: 2, thread id: 0, backtrace: <hidden>]
${DiffColor.CLR.color.code}${DiffColor.ADD.color.code}+ 2 bytes${DiffColor.CLR.color.code}"""
        assertEquals(expectedString, diff.toString())
    }

    @Test
    @Suppress("MaxLineLength")
    fun `toString prints difference with single deallocation`() {
        val trackedHeap = createMatchedAllocAndDeallocScenario()
        val diff = Diff.compute(trackedHeap, "1..1")
        val expectedString =
            """${DiffColor.DEL.color.code}- dealloc[seq no: 0, duration: 0s, address: 00000005, size: 2, thread id: 0, backtrace: <hidden>]
${DiffColor.CLR.color.code}${DiffColor.DEL.color.code}- 2 bytes${DiffColor.CLR.color.code}"""
        assertEquals(expectedString, diff.toString())
    }

    @Test
    @Suppress("MaxLineLength")
    fun `toString prints difference with allocation and deallocation`() {
        val trackedHeap = createUnmatchedAllocAndDeallocScenario()
        val diff = Diff.compute(trackedHeap, "0..1")
        val expectedString =
"""${DiffColor.ADD.color.code}+ alloc[seq no: 0, duration: 0s, address: 00000001, size: 2, thread id: 0, backtrace: <hidden>]
${DiffColor.CLR.color.code}${DiffColor.DEL.color.code}- dealloc[seq no: 0, duration: 0s, address: 00000002, size: 1, thread id: 0, backtrace: <hidden>]
${DiffColor.CLR.color.code}${DiffColor.ADD.color.code}+ 2 bytes${DiffColor.CLR.color.code}, ${DiffColor.DEL.color.code}- 1 bytes${DiffColor.CLR.color.code}"""
        assertEquals(expectedString, diff.toString())
    }

    @Test
    fun `toString prints no-diff if there is no difference`() {
        val trackedHeap = createMatchedAllocAndDeallocScenario()
        val diff = Diff.compute(trackedHeap, "0..1")
        assertEquals(MESSAGE_NO_DIFF, diff.toString())
    }
}
