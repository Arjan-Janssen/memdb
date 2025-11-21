package com.janssen.memdb

import java.util.Locale
import kotlin.math.ceil

const val COLUMN_WIDTH = 8

data class Diff(
    val added: List<TrackedHeap.HeapOperation>,
    val removed: List<TrackedHeap.HeapOperation>,
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
        return builder.toString()
    }

    fun plot(
        dimensions: TrackedHeap.PlotDimensions,
    ): String {
        val builder = StringBuilder()
        val addedByAddress = added.groupBy { it.address }.toSortedMap()
        val removedByAddress = removed.groupBy { it.address }.toSortedMap()
        val addedAndRemovedByAddress = (added + removed).groupBy { it.address }.toSortedMap()

        val minAddress = addedAndRemovedByAddress.firstKey()
        var lastAddress =
            addedAndRemovedByAddress.lastKey() +
                addedAndRemovedByAddress
                    .lastEntry()
                    .value
                    .first()
                    .size
        val maxAddress =
            ceilToMultiple(
                lastAddress,
                dimensions.rows * dimensions.columns,
            )

        println("Address range: $minAddress..$maxAddress")
        val addressRange = maxAddress - minAddress
        val addressRangePerRow = addressRange / dimensions.rows
        val addressRangePerCell = addressRangePerRow / dimensions.columns

        val addedAllocIt = addedByAddress.iterator().peeking()
        val removedAllocIt = removedByAddress.iterator().peeking()
        for (rowStartAddress in minAddress..maxAddress step addressRangePerRow) {
            builder.append(
                plotRow(
                    rowStartAddress,
                    addressRangePerCell,
                    dimensions.columns,
                    addedAllocIt,
                    removedAllocIt,
                ),
            )
        }

        return builder.toString()
    }

    companion object {
        fun ceilToMultiple(
            value: Int,
            multiple: Int,
        ) = ceil(value.toDouble() / multiple).toInt() * multiple

        fun inAddressRange(
            alloc: TrackedHeap.HeapOperation?,
            cellAddressRange: IntRange,
        ): Boolean {
            if (alloc == null) {
                return false
            }
            val lastAllocEnd = alloc.address + alloc.size
            val startWithinCell = alloc.address in cellAddressRange.start..cellAddressRange.last
            val endWithinCell = lastAllocEnd in cellAddressRange
            val rangeOverlapsCell = (alloc.address < cellAddressRange.start && lastAllocEnd > cellAddressRange.start)
            return startWithinCell || endWithinCell || rangeOverlapsCell
        }

        fun advanceItToCell(
            allocIt: PeekingIterator<MutableMap.MutableEntry<Int, List<TrackedHeap.HeapOperation>>>,
            cellStartAddress: Int,
        ) {
            while (allocIt.hasNext() &&
                (
                    allocIt
                        .peek()
                        .value
                        .first()
                        .address +
                        allocIt
                            .peek()
                            .value
                            .first()
                            .size
                ) < cellStartAddress
            ) {
                allocIt.next()
            }
        }

        fun tryPlotCell(
            builder: StringBuilder,
            allocIt: PeekingIterator<MutableMap.MutableEntry<Int, List<TrackedHeap.HeapOperation>>>,
            cellAddressRange: IntRange,
        ): Boolean {
            advanceItToCell(allocIt, cellAddressRange.start)
            if (!allocIt.hasNext()) {
                return false
            }
            val alloc = allocIt.peek().value.first()
            if (!inAddressRange(alloc, cellAddressRange)) {
                return false
            }
            builder.append(
                String.format(
                    Locale.getDefault(),
                    "%${COLUMN_WIDTH-1}d",
                    alloc.seqNo,
                ),
            )
            builder.append('+')
            return true
        }

        fun plotRow(
            rowStartAddress: Int,
            addressRangePerCell: Int,
            columns: Int,
            addedAllocIt: PeekingIterator<MutableMap.MutableEntry<Int, List<TrackedHeap.HeapOperation>>>,
            removedAllocIt: PeekingIterator<MutableMap.MutableEntry<Int, List<TrackedHeap.HeapOperation>>>,
        ): String {
            val builder = StringBuilder()
            builder.append("${rowStartAddress.toInt().toHexString()}: ")
            for (i in 0..<columns) {
                val cellAddressRange = IntRange(rowStartAddress + addressRangePerCell * i,
                                                rowStartAddress + addressRangePerCell * (i + 1) - 1)

                if (tryPlotCell(builder, addedAllocIt, cellAddressRange))
                    continue

                if (tryPlotCell(builder, removedAllocIt, cellAddressRange))
                    continue

                repeat(COLUMN_WIDTH - 1) {
                    builder.append(" ")
                }
                builder.append('.')
            }
            builder.append('\n')
            return builder.toString()
        }

        fun removeAllocation(
            added: MutableSet<TrackedHeap.HeapOperation>,
            removed: MutableSet<TrackedHeap.HeapOperation>,
            dealloc: TrackedHeap.HeapOperation,
        ) {
            if (added
                    .find {
                        it.address == dealloc.address
                    }?.also {
                        added.remove(it)
                    } == null
            ) {
                removed.add(dealloc)
            }
        }

        fun compute(spec: TrackedHeap.DiffSpec): Diff {
            val diffHeap = TrackedHeap.truncate(spec)
            val added = mutableSetOf<TrackedHeap.HeapOperation>()
            val removed = mutableSetOf<TrackedHeap.HeapOperation>()
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
