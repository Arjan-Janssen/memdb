package com.janssen.heap_tracker_client

val COLUMN_WIDTH = 8

data class Diff(val added: List<TrackedHeap.HeapOperation>, val removed: List<TrackedHeap.HeapOperation>)  {
    override fun toString() : String {
        val builder = StringBuilder()
        builder.appendLine("Added:")
        added.forEach {
            builder.appendLine(it.toString())
        }

        builder.appendLine("Removed:")
        removed.forEach {
            builder.appendLine(it.toString())
        }
        return builder.toString();
    }

    fun ceilToMultiple(value: Int, multiple: Int): Int {
        return Math.ceil(value.toDouble() / multiple).toInt() * multiple
    }

    fun inCell(alloc: TrackedHeap.HeapOperation, cellStartAddress:Int, cellEndAddress: Int): Boolean {
        val lastAllocEnd = alloc.address + alloc.size
        val startWithinCell = alloc.address in cellStartAddress..<cellEndAddress
        val endWithinCell = lastAllocEnd in cellStartAddress..<cellEndAddress
        val rangeOverlapsCell = (alloc.address < cellStartAddress && lastAllocEnd > cellStartAddress)
        return startWithinCell || endWithinCell || rangeOverlapsCell;
    }

    fun advanceItToCell(alloc: TrackedHeap.HeapOperation, allocIt: MutableIterator<MutableMap.MutableEntry<Int, List<TrackedHeap.HeapOperation>>>, cellStartAddress: Int, cellEndAddress: Int): TrackedHeap.HeapOperation {
        var lastAlloc = alloc
        while ((alloc.address + alloc.size) < cellStartAddress &&
            allocIt.hasNext()) {
            lastAlloc = allocIt.next().value.first()
        }
        return lastAlloc
    }

    fun plot(columns: Int, rows: Int): String {
        val builder = StringBuilder()
        builder.appendLine("Plot:")

        val addedByAddress = added.groupBy { it.address }.toSortedMap()
        val removedByAddress = removed.groupBy { it.address }.toSortedMap()
        val minAddress = Math.min(added.first().address, removed.first().address)
        val maxAddress = ceilToMultiple(Math.max(removed.last().address + removed.last().size,
                                                added.last().address + added.last().size), rows * columns)
        println("Address range: ${minAddress}...${maxAddress}")

        val addressRange = maxAddress - minAddress
        val addressRangePerRow = addressRange / rows
        val addressRangePerCell = addressRangePerRow / columns

        val addedAllocIt = addedByAddress.iterator()
        var lastAddedAlloc = addedAllocIt.next().value.first()
        val removedAllocIt = removedByAddress.iterator()
        var lastRemovedAlloc = removedAllocIt.next().value.first()
        for (rowStartAddress in minAddress..maxAddress step addressRangePerRow) {
            builder.append("${rowStartAddress.toInt().toHexString()}: ")
            for (i in 0..columns - 1) {
                val cellStartAddress = rowStartAddress + addressRangePerCell * i
                val cellEndAddress = cellStartAddress + addressRangePerCell * (i + 1)

                lastAddedAlloc = advanceItToCell(lastAddedAlloc, addedAllocIt, cellStartAddress, cellEndAddress)
                lastRemovedAlloc = advanceItToCell(lastRemovedAlloc, removedAllocIt, cellStartAddress, cellEndAddress)

                if (inCell(lastAddedAlloc, cellStartAddress, cellEndAddress)) {
                    builder.append(String.format("%${COLUMN_WIDTH}d", lastAddedAlloc.seqNo))
                    builder.append('+')
                }
                else if (inCell(lastRemovedAlloc, cellStartAddress, cellEndAddress)) {
                    builder.append(String.format("%${COLUMN_WIDTH}d", lastRemovedAlloc.seqNo))
                    builder.append('-')
                }
                else {
                    for (i in 0..COLUMN_WIDTH-1) {
                        builder.append(" ")
                    }
                    builder.append('.')
                }
            }
            builder.append('\n')
        }

        return builder.toString()
    }

    companion object {
        fun removeAllocation(added: MutableSet<TrackedHeap.HeapOperation>, removed: MutableSet<TrackedHeap.HeapOperation>, dealloc: TrackedHeap.HeapOperation) {
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