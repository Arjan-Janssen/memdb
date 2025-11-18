package com.janssen.heap_tracker_client

data class Diff(val added: List<TrackedHeap.HeapOperation>, val removed: List<TrackedHeap.HeapOperation>) {

    companion object {
        fun removeAllocation(added: MutableSet<TrackedHeap.HeapOperation>, removed: MutableSet<TrackedHeap.HeapOperation>, dealloc: TrackedHeap.HeapOperation)
        {
            if (added.find {
                it.address == dealloc.address;
            }?.also {
                added.remove(it)
                } == null)
            {
                removed.add(dealloc)
            }
        }
        fun compute(spec: TrackedHeap.DiffSpec) : Diff {
            val added = mutableSetOf<TrackedHeap.HeapOperation>();
            val removed = mutableSetOf<TrackedHeap.HeapOperation>();
            spec.trackedHeap.heapOperations.forEach {
                when (it.kind) {
                    TrackedHeap.HeapOperationKind.Alloc -> {
                        added.add(it)
                    }
                    TrackedHeap.HeapOperationKind.Dealloc -> {
                        removeAllocation(added, removed, it)
                    }
                }
            }
            return Diff(added.toList(), removed.toList())
        }
    }
}