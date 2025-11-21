package com.janssen.memdb

import java.util.Locale

const val COLUMN_WIDTH = 8

data class Diff(
    val added: List<TrackedHeap.HeapOperation>,
    val removed: List<TrackedHeap.HeapOperation>
) {
    override fun toString(): String {
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

    fun plot(columns: Int, rows: Int): String {
        val builder = StringBuilder()
        val addedByAddress = added.groupBy { it.address }.toSortedMap()
        val removedByAddress = removed.groupBy { it.address }.toSortedMap()
        val addedAndRemovedByAddress = (added + removed).groupBy { it.address }.toSortedMap()

        val minAddress = addedAndRemovedByAddress.firstKey()
        var lastAddress = addedAndRemovedByAddress.lastKey() + addedAndRemovedByAddress.lastEntry().value.first().size
        val maxAddress = ceilToMultiple(
            lastAddress,
            rows * columns
        )
        println("Address range: ${minAddress}..${maxAddress}")

        val addressRange = maxAddress - minAddress
        val addressRangePerRow = addressRange / rows
        val addressRangePerCell = addressRangePerRow / columns

        val addedAllocIt = addedByAddress.iterator().peeking()
        val removedAllocIt = removedByAddress.iterator().peeking()
        for (rowStartAddress in minAddress..maxAddress step addressRangePerRow) {
            builder.append(
                plotRow(
                    rowStartAddress,
                    addressRangePerCell,
                    columns,
                    addedAllocIt,
                    removedAllocIt
                )
            )
        }

        return builder.toString()
    }

    companion object {
        fun ceilToMultiple(value: Int, multiple: Int): Int {
            return Math.ceil(value.toDouble() / multiple).toInt() * multiple
        }

        fun inCell(alloc: TrackedHeap.HeapOperation?, cellStartAddress: Int, cellEndAddress: Int): Boolean {
            if (alloc == null) {
                return false;
            }
            val lastAllocEnd = alloc.address + alloc.size
            val startWithinCell = alloc.address in cellStartAddress..<cellEndAddress
            val endWithinCell = lastAllocEnd in cellStartAddress..<cellEndAddress
            val rangeOverlapsCell = (alloc.address < cellStartAddress && lastAllocEnd > cellStartAddress)
            return startWithinCell || endWithinCell || rangeOverlapsCell;
        }

        fun advanceItToCell(
            allocIt: PeekingIterator<MutableMap.MutableEntry<Int, List<TrackedHeap.HeapOperation>>>,
            cellStartAddress: Int
        ) {
            while (allocIt.hasNext() &&
                (allocIt.peek().value.first().address + allocIt.peek().value.first().size) < cellStartAddress
            ) {
                allocIt.next()
            }
        }

        fun plotRow(
            rowStartAddress: Int,
            addressRangePerCell: Int,
            columns: Int,
            addedAllocIt: PeekingIterator<MutableMap.MutableEntry<Int, List<TrackedHeap.HeapOperation>>>,
            removedAllocIt: PeekingIterator<MutableMap.MutableEntry<Int, List<TrackedHeap.HeapOperation>>>
        )
                : String {
            val builder = StringBuilder()
            builder.append("${rowStartAddress.toInt().toHexString()}: ")
            for (i in 0..<columns) {
                val cellStartAddress = rowStartAddress + addressRangePerCell * i
                val cellEndAddress = cellStartAddress + addressRangePerCell * (i + 1)

                advanceItToCell(addedAllocIt, cellStartAddress)
                advanceItToCell(removedAllocIt, cellStartAddress)

                val addedAlloc = addedAllocIt.peek().value.first()
                val removedAlloc = removedAllocIt.peek().value.first()
                if (inCell(addedAlloc, cellStartAddress, cellEndAddress)) {
                    builder.append(
                        String.format(
                            Locale.getDefault(),
                            "%${COLUMN_WIDTH}d",
                            addedAlloc.seqNo
                        )
                    )
                    builder.append('+')
                } else if (inCell(removedAlloc, cellStartAddress, cellEndAddress)) {
                    builder.append(
                        String.format(
                            Locale.getDefault(),
                            "%${COLUMN_WIDTH}d",
                            removedAlloc.seqNo
                        )
                    )
                    builder.append('-')
                } else {
                    for (i in 0..COLUMN_WIDTH - 1) {
                        builder.append(" ")
                    }
                    builder.append('.')
                }
            }
            builder.append('\n')
            return builder.toString()
        }

        fun removeAllocation(
            added: MutableSet<TrackedHeap.HeapOperation>,
            removed: MutableSet<TrackedHeap.HeapOperation>,
            dealloc: TrackedHeap.HeapOperation
        ) {
            if (added.find {
                    it.address == dealloc.address;
                }?.also {
                    added.remove(it)
                } == null) {
                removed.add(dealloc)
            }
        }

        fun compute(spec: TrackedHeap.DiffSpec): Diff {
            val diffHeap = TrackedHeap.truncate(spec)
            val added = mutableSetOf<TrackedHeap.HeapOperation>();
            val removed = mutableSetOf<TrackedHeap.HeapOperation>();
            diffHeap.heapOperations.forEach {
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
