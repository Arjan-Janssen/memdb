package com.janssen.memdb

import java.util.Locale
import java.util.TreeMap
import kotlin.takeHighestOneBit

data class Histogram(
    val frequencyMap: Map<Int, Int>,
) {
    /**
     * Converts the histogram to a string. The string will be in the format:
     * (alloc size: frequency)
     * 1	3
     * 2	3
     * For each line the first number indicates the size of the bucket and the second number the
     * number of allocations that were performed falling within this bucket.
     * @return A pretty-printed string representing the histogram.
     */
    override fun toString() =
        StringBuilder()
            .appendLine("(alloc size:frequency):")
            .apply {
                frequencyMap.forEach {
                    val formattedSize = String.format(Locale.getDefault(), "%10d", it.key)
                    appendLine("${formattedSize}\t${it.value}")
                }
            }.toString()

    companion object {
        internal fun pow2Bucket(value: Int) =
            if (value.takeHighestOneBit() == value) {
                value
            } else {
                value.takeHighestOneBit() shl 1
            }

        /**
         * Builds a histogram of the specified tracked heap. It counts how many heap allocations are performed for
         * each bucket size. Either power-of-two bucketing can be used or the original allocation sizes.
         * When power-of-two bucketing is enabled, allocations will be counted in the smallest power-of-two
         * bucket that encompasses their size.
         *
         * @param trackedHeap The tracked heap for which to compute a histogram.
         * @param buckets Indicates whether to enable power-of-two bucketing for the histogram computation.
         */
        fun build(
            trackedHeap: TrackedHeap,
            buckets: Boolean,
        ) = Histogram(
            TreeMap(
                trackedHeap.heapOperations
                    .filter { it.kind == HeapOperation.Kind.Alloc }
                    .groupingBy { if (buckets) pow2Bucket(it.size) else it.size }
                    .eachCount(),
            ),
        )
    }
}
