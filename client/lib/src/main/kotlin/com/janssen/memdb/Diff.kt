package com.janssen.memdb

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

const val GRAPH_COLUMN_WIDTH = 8

typealias HeapOperationIterator = PeekingIterator<MutableMap.MutableEntry<Int, List<HeapOperation>>>

@ExposedCopyVisibility
data class Diff private constructor(
    val added: List<HeapOperation>,
    val removed: List<HeapOperation>,
) {
    override fun toString() =
        StringBuilder()
            .apply {
                if (added.isEmpty() && removed.isEmpty()) {
                    append(NO_DIFF)
                }
            }.apply {
                if (added.isNotEmpty()) {
                    append(DiffColor.ADD)
                    added.forEach {
                        append("+ ")
                        appendLine(it.toString())
                    }
                    append(DiffColor.CLR)
                }

                if (removed.isNotEmpty()) {
                    append(DiffColor.DEL)
                    removed.forEach {
                        append("- ")
                        appendLine(it.toString())
                    }
                    append(DiffColor.CLR)
                }

                val addedBytes = added.sumOf { it.size }
                val removedBytes = removed.sumOf { it.size }
                append("${DiffColor.ADD.color.code}+$addedBytes${DiffColor.CLR.color.code} bytes, ")
                append("${DiffColor.DEL.color.code}-$removedBytes${DiffColor.CLR.color.code} bytes")
            }.toString()

    fun plot(dimensions: TrackedHeap.PlotDimensions) =
        StringBuilder()
            .apply {
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
                    append(
                        plotRow(
                            rowStartAddress,
                            addressRangePerCell,
                            dimensions.columns,
                            addedAllocIt,
                            removedAllocIt,
                        ),
                    )
                }
            }.toString()

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
            allocIt: HeapOperationIterator,
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
            allocIt: HeapOperationIterator,
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

            builder.apply {
                if (added) {
                    append(AnsiColor.GREEN)
                } else {
                    append(AnsiColor.RED)
                }

                append(
                    String.format(
                        Locale.getDefault(),
                        "%${GRAPH_COLUMN_WIDTH - 1}d",
                        alloc.seqNo,
                    ),
                )
                append(if (added) '+' else '-')
                append(AnsiColor.RESET)
            }
            return true
        }

        @Suppress("LoopWithTooManyJumpStatements")
        private fun plotRow(
            rowStartAddress: Int,
            addressRangePerCell: Int,
            columns: Int,
            addedAllocIt: HeapOperationIterator,
            removedAllocIt: HeapOperationIterator,
        ) = StringBuilder()
            .apply {
                append("${rowStartAddress.toInt().toHexString()}: ")
                for (i in 0..<columns) {
                    val cellAddressRange =
                        IntRange(
                            rowStartAddress + addressRangePerCell * i,
                            rowStartAddress + addressRangePerCell * (i + 1) - 1,
                        )
                    if (tryPlotCellAlloc(this, addedAllocIt, cellAddressRange, true)) {
                        continue
                    }
                    if (tryPlotCellAlloc(this, removedAllocIt, cellAddressRange, false)) {
                        continue
                    }

                    repeat(GRAPH_COLUMN_WIDTH - 1) {
                        append(" ")
                    }
                    append('.')
                }
                appendLine()
            }.toString()

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

        fun compute(
            trackedHeap: TrackedHeap,
            diffSpec: String,
        ): Diff {
            val diffRange = TrackedHeap.Range.fromString(trackedHeap, diffSpec)
            val selectRange =
                TrackedHeap.Range.fromIntRange(
                    trackedHeap,
                    IntRange(
                        min(diffRange.range.first, diffRange.range.last),
                        max(diffRange.range.first, diffRange.range.last),
                    ),
                )
            val selectedHeap = trackedHeap.select(selectRange)
            val added = mutableSetOf<HeapOperation>()
            val removed = mutableSetOf<HeapOperation>()
            selectedHeap.heapOperations.forEach {
                when (it.kind) {
                    HeapOperationKind.Alloc -> {
                        added.add(it)
                    }

                    HeapOperationKind.Dealloc -> {
                        removeAllocation(added, removed, it)
                    }
                }
            }
            if (diffRange.range.first <= diffRange.range.last) {
                return Diff(added.toList(), removed.toList())
            }
            // Handle reversed ranges by swapping added and removed
            return Diff(removed.toList(), added.toList())
        }
    }
}
