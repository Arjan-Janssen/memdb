package com.janssen.heap_tracker_client

import java.util.TreeMap

data class Histogram(val frequencyMap: Map<Long, Int>) {

    companion object {
        fun build(trackedHeap: TrackedHeap) : Histogram {
            val map = trackedHeap.heapOperations.filter { it.kind == TrackedHeap.HeapOperationKind.Alloc }.groupingBy { it.size }.eachCount()
            return Histogram(TreeMap(map))
        }
    }
}