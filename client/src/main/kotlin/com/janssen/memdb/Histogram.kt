package com.janssen.memdb

import java.util.Locale
import java.util.TreeMap

data class Histogram(
    val frequencyMap: Map<Int, Int>,
) {
    override fun toString(): String {
        val builder =
            StringBuilder()
                .appendLine("Histogram (alloc size:frequency):")
        frequencyMap.forEach {
            val formattedSize = String.format(Locale.getDefault(), "%10d", it.key)
            builder.appendLine("${formattedSize}\t${it.value}")
        }
        return builder.toString()
    }

    companion object {
        fun build(trackedHeap: TrackedHeap): Histogram {
            val map =
                trackedHeap.heapOperations
                    .filter { it.kind == TrackedHeap.HeapOperationKind.Alloc }
                    .groupingBy { it.size }
                    .eachCount()
            return Histogram(TreeMap(map))
        }
    }
}
