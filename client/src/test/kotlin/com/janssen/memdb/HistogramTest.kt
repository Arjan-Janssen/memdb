package com.janssen.memdb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HistogramTest {
    @Test
    fun pow2BucketReturnsEncompassingPow2() {
        assertEquals(0, Histogram.pow2Bucket(0))
        assertEquals(1, Histogram.pow2Bucket(1))
        assertEquals(2, Histogram.pow2Bucket(2))
        assertEquals(4, Histogram.pow2Bucket(3))
        assertEquals(4, Histogram.pow2Bucket(4))
        assertEquals(8, Histogram.pow2Bucket(5))
        assertEquals(128, Histogram.pow2Bucket(100))
        assertEquals(65536, Histogram.pow2Bucket(65535))
    }

    @Test
    fun buildReturnsEmptyHistogram() {
        val emptyTrackedHeap = TrackedHeap.Builder().build()
        val histogram = Histogram.build(emptyTrackedHeap, false)
        assertEquals(0, histogram.frequencyMap.size)
    }

    @Test
    fun buildReturnsSingleSizeHistogram() {
        val trackedHeap =
            TrackedHeap
                .Builder()
                .addHeapOperation(
                    HeapOperation.Builder().alloc(0, 1),
                ).build()
        val histogram = Histogram.build(trackedHeap, false)

        assertEquals(1, histogram.frequencyMap.size)
        assertTrue(histogram.frequencyMap.contains(1))
        assertEquals(1, histogram.frequencyMap[1])
    }

    @Test
    fun buildReturnsUniqueSizesHistogram() {
        val trackedHeap =
            TrackedHeap
                .Builder()
                .addHeapOperation(HeapOperation.Builder().alloc(0, 1))
                .addHeapOperation(HeapOperation.Builder().alloc(1, 2))
                .addHeapOperation(HeapOperation.Builder().alloc(1, 3))
                .build()
        val histogram = Histogram.build(trackedHeap, false)

        assertEquals(3, histogram.frequencyMap.size)
        assertTrue(histogram.frequencyMap.contains(1))
        assertEquals(1, histogram.frequencyMap[1])
        assertTrue(histogram.frequencyMap.contains(2))
        assertEquals(1, histogram.frequencyMap[2])
        assertTrue(histogram.frequencyMap.contains(3))
        assertEquals(1, histogram.frequencyMap[3])
    }

    @Test
    fun buildReturnsDuplicateSizesHistogram() {
        val allocSize = 2
        val trackedHeap =
            TrackedHeap
                .Builder()
                .addHeapOperation(HeapOperation.Builder().alloc(0, allocSize))
                .addHeapOperation(HeapOperation.Builder().alloc(1, allocSize))
                .build()

        val histogram = Histogram.build(trackedHeap, false)
        assertEquals(1, histogram.frequencyMap.size)
        assertTrue(histogram.frequencyMap.contains(allocSize))
        assertEquals(2, histogram.frequencyMap[allocSize])
    }

    private fun createTrackedHeapWithIncreasingAllocs(range: IntRange): TrackedHeap {
        val builder = TrackedHeap.Builder()
        range.forEachIndexed { index, size ->
            val address = index * (range.last + 1)
            builder.addHeapOperation(HeapOperation.Builder().alloc(address, size))
        }
        return builder.build()
    }

    @Test
    fun buildWithBucketsReturnsPow2Histogram() {
        val range = IntRange(1, 5)
        val trackedHeap = createTrackedHeapWithIncreasingAllocs(range)
        val histogram = Histogram.build(trackedHeap, true)

        assertEquals(4, histogram.frequencyMap.size)
        assertTrue(histogram.frequencyMap.contains(1))
        assertEquals(1, histogram.frequencyMap[1])
        assertTrue(histogram.frequencyMap.contains(2))
        assertEquals(1, histogram.frequencyMap[2])
        assertFalse(histogram.frequencyMap.contains(3))
        assertTrue(histogram.frequencyMap.contains(4))
        assertEquals(2, histogram.frequencyMap[4])
        assertFalse(histogram.frequencyMap.contains(5))
        assertFalse(histogram.frequencyMap.contains(6))
        assertFalse(histogram.frequencyMap.contains(7))
        assertTrue(histogram.frequencyMap.contains(8))
        assertEquals(1, histogram.frequencyMap[8])
    }

    @Test
    fun toStringReturnsReadableString() {
        val range = IntRange(1, 5)
        val trackedHeap = createTrackedHeapWithIncreasingAllocs(range)
        val histogram = Histogram.build(trackedHeap, false)
        val expectedString =
"""(alloc size:frequency):
         1	1
         2	1
         3	1
         4	1
         5	1
"""
        assertEquals(expectedString, histogram.toString())
    }
}
