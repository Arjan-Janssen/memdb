package com.janssen.memdb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class TrackedHeapBuilderTest {
    @Test
    fun builderCreatesValidTrackedHeap() {
        val expectedBeginMarker = Marker(0, "begin")
        val expectedEndMarker = Marker(10, "end")
        val expectedMarkers =
            listOf(
                Marker(5, "before"),
                Marker(7, "after"),
            )
        val initialSequenceNumber = 5
        val numBuilderOperations = 2
        val expectedAlloc =
            HeapOperation(
                initialSequenceNumber + numBuilderOperations,
                HeapOperationKind.Alloc,
                durationSinceServerStart = 200.toDuration(DurationUnit.MILLISECONDS),
                address = 2,
                size = 4,
                threadId = 5,
                backtrace = "alloc backtrace",
            )
        val expectedDealloc =
            HeapOperation(
                initialSequenceNumber + numBuilderOperations + 1,
                HeapOperationKind.Dealloc,
                durationSinceServerStart = 400.toDuration(DurationUnit.MILLISECONDS),
                address = 10,
                size = 1,
                threadId = 6,
                backtrace = "dealloc backtrace",
            )
        val expectedHeapOperations =
            listOf(
                HeapOperation(
                    initialSequenceNumber + numBuilderOperations,
                    HeapOperationKind.Alloc,
                    durationSinceServerStart = 200.toDuration(DurationUnit.MILLISECONDS),
                    address = 2,
                    size = 4,
                    threadId = 5,
                    backtrace = "alloc backtrace",
                ),
                HeapOperation(
                    initialSequenceNumber + numBuilderOperations + 1,
                    HeapOperationKind.Dealloc,
                    durationSinceServerStart = 400.toDuration(DurationUnit.MILLISECONDS),
                    address = 10,
                    size = 1,
                    threadId = 6,
                    backtrace = "dealloc backtrace",
                ),
            )

        val heapOperationBuilder = HeapOperation.Builder(initialSequenceNumber)
        val trackedHeap =
            TrackedHeap
                .Builder()
                .addHeapOperation(heapOperationBuilder.alloc(1, 2))
                .addHeapOperation(heapOperationBuilder.dealloc(1))
                .addHeapOperation(expectedAlloc)
                .addHeapOperation(expectedDealloc)
                .addHeapOperations(expectedHeapOperations)
                .addMarker(expectedBeginMarker)
                .addMarker(expectedEndMarker)
                .addMarkers(expectedMarkers)
                .build()

        assertEquals(6, trackedHeap.heapOperations.size)
        assertEquals(initialSequenceNumber, trackedHeap.heapOperations[0].seqNo)
        assertEquals(initialSequenceNumber + 1, trackedHeap.heapOperations[1].seqNo)
        assertEquals(expectedAlloc, trackedHeap.heapOperations[2])
        assertEquals(expectedDealloc, trackedHeap.heapOperations[3])
        assertEquals(expectedHeapOperations.first(), trackedHeap.heapOperations[4])
        assertEquals(expectedHeapOperations.last(), trackedHeap.heapOperations[5])
        assertEquals(
            listOf(expectedBeginMarker, expectedEndMarker) + expectedMarkers,
            trackedHeap.markers,
        )
    }
}

class TrackedHeapTest {
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
                    size = 1,
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
    fun toStringReturnsReadableString() {
        val trackedHeap = createTrackedHeap()
        val expectedString =
"""heap operations:
  alloc[seq no: 0, kind: Alloc, duration: 200ms, address: 00000002, size: 4, thread id: 5, backtrace: (hidden)] -> 4
  dealloc[seq no: 1, kind: Dealloc, duration: 400ms, address: 0000000a, size: 1, thread id: 6, backtrace: (hidden)] -> 3

markers:
  marker[name: begin, seq-no: 0]
  marker[name: end, seq-no: 1]"""
        assertEquals(expectedString, trackedHeap.toString())
    }

    @Test
    fun protobufRoundTripCommutes() {
        val expectedTrackedHeap = createTrackedHeap()
        assertEquals(
            expectedTrackedHeap,
            TrackedHeap.fromProtobuf(expectedTrackedHeap.toProtobuf()),
        )
    }
}
