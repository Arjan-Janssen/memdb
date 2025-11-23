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

const val DEFAULT_TEST_COLUMNS = 20
const val DEFAULT_TEST_ROWS = 20

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
    private fun createFollowingTrackedHeap() =
        TrackedHeap(
            listOf(
                HeapOperation(
                    2,
                    HeapOperationKind.Alloc,
                    durationSinceServerStart = 600.toDuration(DurationUnit.MILLISECONDS),
                    address = 64,
                    size = 2,
                    threadId = 1,
                    backtrace = "backtrace-2",
                ),
                HeapOperation(
                    3,
                    HeapOperationKind.Alloc,
                    durationSinceServerStart = 1000.toDuration(DurationUnit.MILLISECONDS),
                    address = 128,
                    size = 2,
                    threadId = 2,
                    backtrace = "backtrace-3",
                ),
            ),
            markers =
                listOf(
                    Marker(2, "special"),
                ),
        )

    @Test
    fun concatenateJoinsTrackedHeaps() {
        val trackedHeap0 = createTrackedHeap()
        val trackedHeap1 = createFollowingTrackedHeap()
        val concatenated = TrackedHeap.concatenate(trackedHeap0, trackedHeap1)
        assertEquals(
            concatenated.heapOperations,
            trackedHeap0.heapOperations + trackedHeap1.heapOperations,
        )
        assertEquals(
            concatenated.markers,
            trackedHeap0.markers + trackedHeap1.markers,
        )

    }

    @Test
    fun toStringReturnsReadableString() {
        val trackedHeap = createTrackedHeap()
        val expectedString =
"""heap operations:
  alloc[seq no: 0, kind: Alloc, duration: 200ms, address: 00000002, size: 4, thread id: 5, backtrace: (hidden)] -> 4
  dealloc[seq no: 1, kind: Dealloc, duration: 400ms, address: 0000000a, size: 2, thread id: 6, backtrace: (hidden)] -> 2

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

    @Test
    fun plotGraphFullGraph() {
        val trackedHeap = createTrackedHeap()
        val expectedGraph =
""" allocated->                    <-4
     begin: --------------------
         0: ####################
       end: --------------------
         1: ##########
"""
        assertEquals(
            expectedGraph,
            trackedHeap.plotGraph(
                IntRange(0, 1),
                TrackedHeap.PlotDimensions(DEFAULT_TEST_COLUMNS, DEFAULT_TEST_ROWS),
            ),
        )
    }

    @Test
    fun plotGraphSubrange() {
        val trackedHeap = createTrackedHeap()
        val expectedGraph =
            """ allocated->                    <-4
       end: --------------------
         1: ##########
"""
        assertEquals(
            expectedGraph,
            trackedHeap.plotGraph(
                IntRange(1, 1),
                TrackedHeap.PlotDimensions(DEFAULT_TEST_COLUMNS, DEFAULT_TEST_ROWS),
            ),
        )
    }


    @Test
    fun plotGraphHalfColumns() {
        val trackedHeap = createTrackedHeap()
        val expectedGraph =
            """ allocated->          <-4
     begin: ----------
         0: ##########
       end: ----------
         1: #####
"""
        assertEquals(
            expectedGraph,
            trackedHeap.plotGraph(
                IntRange(0, 1),
                TrackedHeap.PlotDimensions(DEFAULT_TEST_ROWS shr 1, 2),
            ),
        )
    }

}
