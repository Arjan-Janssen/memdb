package com.janssen.memdb

import com.janssen.memdb.TrackedHeap.RangeSpec
import java.util.Locale
import kotlin.collections.emptyList
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

const val COLUMN_WIDTH = 8

data class Diff(
    val added: List<HeapOperation>,
    val removed: List<HeapOperation>,
) {
    override fun toString(): String {
        val builder =
            StringBuilder()

        if (added.isEmpty() && removed.isEmpty()) {
            builder.append(NO_DIFF)
        }

        if (added.isNotEmpty()) {
            builder.append(AnsiColor.GREEN)
            added.forEach {
                builder
                    .append("+ ")
                    .appendLine(it.toString())
            }
            builder.append(AnsiColor.RESET)
        }

        if (removed.isNotEmpty()) {
            builder.append(AnsiColor.RED)
            removed.forEach {
                builder
                    .append("- ")
                    .appendLine(it.toString())
            }
            builder.append(AnsiColor.RESET)
        }
        return builder.toString()
    }

    fun plot(dimensions: TrackedHeap.PlotDimensions): String {
        val builder = StringBuilder()
        val addedByAddress = added.groupBy { it.address }.toSortedMap()
        val removedByAddress = removed.groupBy { it.address }.toSortedMap()
        val addedAndRemovedByAddress = (added + removed).groupBy { it.address }.toSortedMap()
        if (addedAndRemovedByAddress.isEmpty()) {
            return NO_DIFF
        }
        val minAddress = addedAndRemovedByAddress.firstKey()
        val lastAddress =
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
        private fun ceilToMultiple(
            value: Int,
            multiple: Int,
        ) = ceil(value.toDouble() / multiple).toInt() * multiple

        fun inAddressRange(
            alloc: HeapOperation?,
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

        private fun advanceItToCell(
            allocIt: PeekingIterator<MutableMap.MutableEntry<Int, List<HeapOperation>>>,
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

        private fun tryPlotCellAlloc(
            builder: StringBuilder,
            allocIt: PeekingIterator<MutableMap.MutableEntry<Int, List<HeapOperation>>>,
            cellAddressRange: IntRange,
            added: Boolean,
        ): Boolean {
            advanceItToCell(allocIt, cellAddressRange.start)
            if (!allocIt.hasNext()) {
                return false
            }
            val alloc = allocIt.peek().value.first()
            if (!inAddressRange(alloc, cellAddressRange)) {
                return false
            }
            if (added) {
                builder.append(AnsiColor.GREEN)
            } else {
                builder.append(AnsiColor.RED)
            }

            builder.append(
                String.format(
                    Locale.getDefault(),
                    "%${COLUMN_WIDTH - 1}d",
                    alloc.seqNo,
                ),
            )
            builder.append(if (added) '+' else '-')
            builder.append(AnsiColor.RESET)
            return true
        }

        @Suppress("LoopWithTooManyJumpStatements")
        private fun plotRow(
            rowStartAddress: Int,
            addressRangePerCell: Int,
            columns: Int,
            addedAllocIt: PeekingIterator<MutableMap.MutableEntry<Int, List<HeapOperation>>>,
            removedAllocIt: PeekingIterator<MutableMap.MutableEntry<Int, List<HeapOperation>>>,
        ): String {
            val builder = StringBuilder()
            builder.append("${rowStartAddress.toInt().toHexString()}: ")
            for (i in 0..<columns) {
                val cellAddressRange =
                    IntRange(
                        rowStartAddress + addressRangePerCell * i,
                        rowStartAddress + addressRangePerCell * (i + 1) - 1,
                    )
                if (tryPlotCellAlloc(builder, addedAllocIt, cellAddressRange, true)) {
                    continue
                }
                if (tryPlotCellAlloc(builder, removedAllocIt, cellAddressRange, false)) {
                    continue
                }

                repeat(COLUMN_WIDTH - 1) {
                    builder.append(" ")
                }
                builder.append('.')
            }
            builder.appendLine()
            return builder.toString()
        }

        private fun removeAllocation(
            added: MutableSet<HeapOperation>,
            removed: MutableSet<HeapOperation>,
            dealloc: HeapOperation,
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

        fun compute(spec: TrackedHeap.RangeSpec): Diff {
            // Diff spec refers to the state of the heap before the heap operation with the
            // sequence number is applied.
            fun toTruncateRange(diffSpec: RangeSpec): RangeSpec? {
                val trackedHeap = diffSpec.trackedHeap
                val rangeMin = min(diffSpec.range.first, diffSpec.range.last)
                val rangeMax = max(diffSpec.range.first, diffSpec.range.last)
                if (rangeMax == rangeMin) {
                    return null
                }
                val truncateRange = IntRange(rangeMin, rangeMax - 1)
                return RangeSpec(trackedHeap, truncateRange)
            }

            val truncateSpec = toTruncateRange(spec)
            if (truncateSpec == null) {
                return Diff(emptyList(), emptyList())
            }
            val diffHeap = TrackedHeap.truncate(truncateSpec)
            val added = mutableSetOf<HeapOperation>()
            val removed = mutableSetOf<HeapOperation>()
            diffHeap.heapOperations.forEach {
                when (it.kind) {
                    HeapOperationKind.Alloc -> {
                        added.add(it)
                    }

                    HeapOperationKind.Dealloc -> {
                        removeAllocation(added, removed, it)
                    }
                }
            }
            return Diff(added.toList(), removed.toList())
        }
    }
}
