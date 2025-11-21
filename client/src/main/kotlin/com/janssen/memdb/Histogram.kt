package com.janssen.memdb

import java.util.Locale
import java.util.TreeMap
import kotlin.takeHighestOneBit

data class Histogram(
    val frequencyMap: Map<Int, Int>,
) {
    override fun toString(): String {
        val builder =
            StringBuilder()
                .appendLine("(alloc size:frequency):")
        frequencyMap.forEach {
            val formattedSize = String.format(Locale.getDefault(), "%10d", it.key)
            builder.appendLine("${formattedSize}\t${it.value}")
        }
        return builder.toString()
    }

    companion object {
        fun pow2Bucket(value: Int):  Int =
            if (value.takeHighestOneBit() == value) value else value.takeHighestOneBit() shl 1

        fun build(trackedHeap: TrackedHeap, buckets: Boolean): Histogram {
            val map =
                trackedHeap.heapOperations
                    .filter { it.kind == TrackedHeap.HeapOperationKind.Alloc }
                    .groupingBy { if (buckets) pow2Bucket(it.size) else it.size}
                    .eachCount()
            return Histogram(TreeMap(map))
        }
    }
}
