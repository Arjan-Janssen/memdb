package com.janssen.memdb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
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
                val invalidRange = IntRange(1, 2)

                TrackedHeap.Range.fromIntRange(
                    trackedHeap,
                    invalidRange,
                )
            }
        assertEquals("Invalid to-position 2 in range", exception.message)
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
            "Invalid from-position in diff spec arjan..1",
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
            "Invalid to-position in diff spec 0..arjan",
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
    fun `rangeStartPosition with valid marker name`() {
        val trackedHeap = createTrackedHeap()
        assertEquals(1, trackedHeap.rangeStartPosition("end"))
    }

    @Test
    fun `rangeStartPosition with invalid marker name`() {
        val trackedHeap = createTrackedHeap()
        assertNull(trackedHeap.rangeStartPosition("arjan"))
    }

    @Test
    fun `rangeStartPosition with valid index`() {
        val trackedHeap = createTrackedHeap()
        assertEquals(0, trackedHeap.rangeStartPosition("0"))
    }

    @Test
    fun `rangeStartPosition with invalid index`() {
        val trackedHeap = createTrackedHeap()
        // Invalid indices are permitted. The function does not do range checking
        assertEquals(1982, trackedHeap.rangeStartPosition("1982"))
        assertEquals(-1, trackedHeap.rangeStartPosition("-1"))
    }

    @Test
    fun `rangeEndPosition with valid marker name`() {
        val trackedHeap = createTrackedHeap()
        assertEquals(0, trackedHeap.rangeEndPosition("end", false))
    }

    @Test
    fun `rangeEndPosition with invalid marker name`() {
        val trackedHeap = createTrackedHeap()
        assertNull(trackedHeap.rangeEndPosition("arjan", false))
    }

    @Test
    fun `rangeEndPosition with valid index`() {
        val trackedHeap = createTrackedHeap()
        assertEquals(1, trackedHeap.rangeEndPosition("1", false))
    }

    @Test
    fun `rangeEndPosition with invalid index`() {
        val trackedHeap = createTrackedHeap()
        // Invalid indices are permitted. The function does not do range checking
        assertEquals(1982, trackedHeap.rangeEndPosition("1982", false))
        assertEquals(-1, trackedHeap.rangeEndPosition("-1", false))
    }

    @Test
    fun `concatenate with 2 tracked heaps`() {
        val trackedHeap0 = createTrackedHeap()
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
        val trackedHeap = createTrackedHeap()
        val truncatedHeap =
            TrackedHeap.truncate(
                TrackedHeap.Range.fromIntRange(
                    trackedHeap,
                    IntRange(1, 1),
                ),
            )
        assertEquals(1, truncatedHeap.heapOperations.size)
        assertEquals(trackedHeap.heapOperations[1], truncatedHeap.heapOperations[0])
    }

    @Test
    fun `marker with valid name`() {
        val trackedHeap = createTrackedHeap()
        val validName = "begin"
        val marker = trackedHeap.marker(validName)
        assertNotNull(marker)
        assertEquals(validName, marker?.name)
    }

    @Test
    fun `marker with invalid name`() {
        val trackedHeap = createTrackedHeap()
        val invalidName = "invalid"
        assertNull(trackedHeap.marker(invalidName))
    }

    @Test
    fun `marker with valid sequence number`() {
        val trackedHeap = createTrackedHeap()
        val validSeqNo = 0
        val marker = trackedHeap.marker(validSeqNo)
        assertNotNull(marker)
        val expectedName = "begin"
        assertEquals(expectedName, marker?.name)
    }

    @Test
    fun `marker with invalid sequence number`() {
        val trackedHeap = createTrackedHeap()
        val invalidSeqNo = 1982
        assertNull(trackedHeap.marker(invalidSeqNo))
    }

    @Test
    fun `toString returns a readable string`() {
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
    fun `protobuf round-trip commutes`() {
        val expectedTrackedHeap = createTrackedHeap()
        assertEquals(
            expectedTrackedHeap,
            TrackedHeap.fromProtobuf(expectedTrackedHeap.toProtobuf()),
        )
    }

    @Test
    fun `plotGraph with full range`() {
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
    fun `plotGraph with smaller sub-range`() {
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
    fun `plotGraph with half the number of columns`() {
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
