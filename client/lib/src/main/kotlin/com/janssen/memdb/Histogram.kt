package com.janssen.memdb

import java.util.Locale
import java.util.TreeMap
import kotlin.takeHighestOneBit

data class Histogram(
    val frequencyMap: Map<Int, Int>,
) {
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
