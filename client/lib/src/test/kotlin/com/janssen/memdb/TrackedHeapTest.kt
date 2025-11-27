package com.janssen.memdb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.text.ParseException
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class TrackedHeapBuilderTest {
    @Test
    fun `build creates valid tracked heap`() {
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

class TrackedHeapRangeTest {
    private fun createSimpleTrackedHeap() =
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
                    Marker(2, "end"),
                ),
        )

    @Test
    fun `fromIntRange with a valid range`() {
        val trackedHeap = createSimpleTrackedHeap()
        val expectedRange = IntRange(0, 0)
        val range =
            TrackedHeap.Range.fromIntRange(
                trackedHeap,
                expectedRange,
            )
        assertEquals(expectedRange, range.range)
    }

    @Test
    fun `fromIntRange with an invalid end position`() {
        val exception =
            assertThrows(
                ParseException::class.java,
            ) {
                val trackedHeap = createSimpleTrackedHeap()
                val invalidRange = IntRange(1, 3)

                TrackedHeap.Range.fromIntRange(
                    trackedHeap,
                    invalidRange,
                )
            }
        assertEquals("Invalid to-position 3 in range", exception.message)
    }

    @Test
    fun `fromIntRange with an invalid start position`() {
        val exception =
            assertThrows(
                ParseException::class.java,
            ) {
                val trackedHeap = createSimpleTrackedHeap()
                val invalidRange = IntRange(3, 1)

                TrackedHeap.Range.fromIntRange(
                    trackedHeap,
                    invalidRange,
                )
            }
        assertEquals("Invalid from-position 3 in range", exception.message)
    }

    @Test
    fun `fromString with a valid integer range`() {
        val trackedHeap = createSimpleTrackedHeap()
        val expectedFromPosition = 0
        val expectedToPosition = 1
        val range =
            TrackedHeap.Range.fromString(
                trackedHeap,
                "$expectedFromPosition..$expectedToPosition",
            )
        assertEquals(expectedFromPosition, range.range.first)
        assertEquals(expectedToPosition, range.range.last)
    }

    @Test
    fun `fromString with valid marker names`() {
        val trackedHeap = createSimpleTrackedHeap()
        val expectedFromPosition = 0
        val expectedToPosition = 1
        val range =
            TrackedHeap.Range.fromString(
                trackedHeap,
                "begin..end",
            )
        assertEquals(expectedFromPosition, range.range.first)
        assertEquals(expectedToPosition, range.range.last)
    }

    @Test
    fun `fromString with invalid from-marker`() {
        val exception =
            assertThrows(ParseException::class.java) {
                val trackedHeap = createSimpleTrackedHeap()
                TrackedHeap.Range.fromString(
                    trackedHeap,
                    "arjan..1",
                )
            }
        assertEquals(
            "Invalid from-position in range spec arjan..1",
            exception.message,
        )
    }

    @Test
    fun `fromString with invalid to-marker`() {
        val exception =
            assertThrows(ParseException::class.java) {
                val trackedHeap = createSimpleTrackedHeap()
                TrackedHeap.Range.fromString(
                    trackedHeap,
                    "0..arjan",
                )
            }
        assertEquals(
            "Invalid to-position in range spec 0..arjan",
            exception.message,
        )
    }

    @Test
    fun `fromString with invalid from-position`() {
        val exception =
            assertThrows(ParseException::class.java) {
                val trackedHeap = createSimpleTrackedHeap()
                TrackedHeap.Range.fromString(
                    trackedHeap,
                    "5..0",
                )
            }
        assertEquals(
            "Invalid from-position 5 in range",
            exception.message,
        )
    }

    @Test
    fun `fromString with invalid to-position`() {
        val exception =
            assertThrows(ParseException::class.java) {
                val trackedHeap = createSimpleTrackedHeap()
                TrackedHeap.Range.fromString(
                    trackedHeap,
                    "0..5",
                )
            }
        assertEquals(
            "Invalid to-position 5 in range",
            exception.message,
        )
    }
}

const val DEFAULT_TEST_COLUMNS = 20
const val DEFAULT_TEST_ROWS = 20

class TrackedHeapTest {
    private fun createMatchingAllocDeallocPair() =
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
                    address = 2,
                    size = 4,
                    threadId = 6,
                    backtrace = "dealloc backtrace",
                ),
            ),
            markers =
                listOf(
                    Marker(0, "begin", index = 0),
                    Marker(1, "end", index = 1),
                ),
        )

    private fun createMismatchedDealloc() =
        TrackedHeap(
            listOf(
                HeapOperation(
                    0,
                    HeapOperationKind.Dealloc,
                    durationSinceServerStart = 400.toDuration(DurationUnit.MILLISECONDS),
                    address = 2,
                    size = 4,
                    threadId = 6,
                    backtrace = "dealloc backtrace",
                ),
            ),
            markers =
                emptyList<Marker>(),
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
    fun `from position with valid marker name`() {
        val trackedHeap = createMatchingAllocDeallocPair()
        assertEquals(0, trackedHeap.fromPosition("begin"))
    }

    @Test
    fun `from position with invalid marker name`() {
        val trackedHeap = createMatchingAllocDeallocPair()
        assertNull(trackedHeap.fromPosition("arjan"))
    }

    @Test
    fun `from position with valid index`() {
        val trackedHeap = createMatchingAllocDeallocPair()
        assertEquals(1, trackedHeap.fromPosition("1"))
    }

    @Test
    fun `from position with invalid index`() {
        val trackedHeap = createMatchingAllocDeallocPair()
        // Invalid indices are permitted. The function does not do range checking
        assertEquals(1982, trackedHeap.fromPosition("1982"))
        assertEquals(-1, trackedHeap.fromPosition("-1"))
    }

    @Test
    fun `to position with valid marker at seq no 0`() {
        val trackedHeap = createMatchingAllocDeallocPair()
        assertEquals(0, trackedHeap.toPosition("begin"))
    }

    @Test
    fun `to position with valid marker at nonzero seq no`() {
        val trackedHeap = createMatchingAllocDeallocPair()
        assertEquals(0, trackedHeap.toPosition("end:1"))
        assertEquals(trackedHeap.fromPosition("end:1")?.minus(1), trackedHeap.toPosition("end:1"))
    }

    @Test
    fun `to position with invalid marker name`() {
        val trackedHeap = createMatchingAllocDeallocPair()
        assertNull(trackedHeap.toPosition("arjan"))
    }

    @Test
    fun `to position with valid index`() {
        val trackedHeap = createMatchingAllocDeallocPair()
        assertEquals(1, trackedHeap.toPosition("1"))
    }

    @Test
    fun `to position with invalid index`() {
        val trackedHeap = createMatchingAllocDeallocPair()
        // Invalid indices are permitted. The function does not do range checking
        assertEquals(1982, trackedHeap.toPosition("1982"))
        assertEquals(-1, trackedHeap.toPosition("-1"))
    }

    @Test
    fun `concatenate with 2 tracked heaps`() {
        val trackedHeap0 = createMatchingAllocDeallocPair()
        val trackedHeap1 = createFollowingTrackedHeap()
        val concatenated = TrackedHeap.concatenate(listOf(trackedHeap0, trackedHeap1))
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
    fun `truncate with single element range`() {
        val trackedHeap = createMatchingAllocDeallocPair()
        val truncatedHeap =
            TrackedHeap.truncate(
                TrackedHeap.Range.fromIntRange(
                    trackedHeap,
                    IntRange(1, 1),
                ),
            )
        assertEquals(1, truncatedHeap.heapOperations.size)
    }

    @Test
    fun `marker with valid name`() {
        val trackedHeap = createMatchingAllocDeallocPair()
        val validName = "begin"
        val marker = trackedHeap.marker(validName)
        assertNotNull(marker)
        assertEquals(validName, marker?.name)
    }

    @Test
    fun `marker with valid name and index`() {
        val trackedHeap = createMatchingAllocDeallocPair()
        val validName = "end"
        val marker = trackedHeap.marker(validName, 1)
        assertNotNull(marker)
        assertEquals(validName, marker?.name)
    }

    @Test
    fun `marker with invalid name`() {
        val trackedHeap = createMatchingAllocDeallocPair()
        val invalidName = "invalid"
        assertNull(trackedHeap.marker(invalidName))
    }

    @Test
    fun `marker with valid sequence number`() {
        val trackedHeap = createMatchingAllocDeallocPair()
        val validSeqNo = 0
        val markers = trackedHeap.markers(validSeqNo)
        assertEquals(1, markers.size)
        val expectedName = "begin"
        assertEquals(expectedName, markers.first().name)
    }

    @Test
    fun `marker with invalid sequence number`() {
        val trackedHeap = createMatchingAllocDeallocPair()
        val invalidSeqNo = 1982
        assertTrue(trackedHeap.markers(invalidSeqNo).isEmpty())
    }

    @Test
    fun `toString returns a readable string`() {
        val trackedHeap = createMatchingAllocDeallocPair()
        val expectedString =
"""heap operations:
  alloc[seq no: 0, duration: 200ms, address: 00000002, size: 4, thread id: 5, backtrace: <hidden>] -> 4
  dealloc[seq no: 1, duration: 400ms, address: 00000002, size: 4, thread id: 6, backtrace: <hidden>] -> 0

markers:
  marker[name: begin, index: 0, seq-no: 0]
  marker[name: end, index: 1, seq-no: 1]"""
        assertEquals(expectedString, trackedHeap.toString())
    }

    @Test
    fun `protobuf round-trip commutes`() {
        val expectedTrackedHeap = createMatchingAllocDeallocPair()
        assertEquals(
            expectedTrackedHeap,
            TrackedHeap.fromProtobuf(expectedTrackedHeap.toProtobuf()),
        )
    }

    @Test
    fun `plotGraph with full range`() {
        val trackedHeap = createMatchingAllocDeallocPair()
        val expectedGraph =
"""       allocated->                    <-4
           begin: --------------------
               0: ${DiffColor.ADD.color.code}*+++++++++++++++++++${AnsiColor.RESET.code}
           end:1: --------------------
               1: ${DiffColor.DEL.color.code}-------------------*${AnsiColor.RESET.code}
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
    fun `plotGraph with smaller sub-range`() {
        val trackedHeap = createMatchingAllocDeallocPair()
        val expectedGraph =
"""       allocated->                    <-4
           begin: --------------------
               0: ${DiffColor.ADD.color.code}*+++++++++++++++++++${AnsiColor.RESET.code}
           end:1: --------------------
"""
        assertEquals(
            expectedGraph,
            trackedHeap.plotGraph(
                IntRange(0, 0),
                TrackedHeap.PlotDimensions(DEFAULT_TEST_COLUMNS, DEFAULT_TEST_ROWS),
            ),
        )
    }

    @Test
    fun `plotGraph with mismatching dealloc`() {
        val trackedHeap = createMismatchedDealloc()
        val expectedGraph =
            """       allocated->          <-0
               0: 
"""
        assertEquals(
            expectedGraph,
            trackedHeap.plotGraph(
                IntRange(0, 0),
                TrackedHeap.PlotDimensions(DEFAULT_TEST_ROWS shr 1, 2),
            ),
        )
    }

    @Test
    fun `plotGraph with half the number of columns`() {
        val trackedHeap = createMatchingAllocDeallocPair()
        val expectedGraph =
            """       allocated->          <-4
           begin: ----------
               0: ${DiffColor.ADD.color.code}*+++++++++${AnsiColor.RESET.code}
           end:1: ----------
               1: ${DiffColor.DEL.color.code}---------*${AnsiColor.RESET.code}
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
