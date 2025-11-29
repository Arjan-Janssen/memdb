package com.janssen.memdb

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

internal const val DEFAULT_PLOT_LAYOUT_COLUMN_WIDTH = 8
internal typealias HeapOperationIterator = PeekingIterator<MutableMap.MutableEntry<Int, List<HeapOperation>>>

/**
 * Represents a change (diff) in heap memory. A change can add (+) or remove (-) allocations or
 * deallocations relative to the initial memory state.
 */
@ExposedCopyVisibility
data class Diff private constructor(
    val added: List<HeapOperation>,
    val removed: List<HeapOperation>,
) {
    /**
     * Converts the diff to a pretty-printed string. ANSI colors are used to make the text more
     * easily readable.
     *
     * For example:\
     * `+ alloc[seq no: 0, duration: 0s, address: 00000001, size: 2, thread id: 0, backtrace: <hidden>]`\
     * `- dealloc[seq no: 0, duration: 0s, address: 00000002, size: 1, thread id: 0, backtrace: <hidden>]`\
     * `+ 2 bytes, -1 bytes`
     *
     * Indicates that a single allocation was added and a single deallocation was removed compared to the initial
     * memory state. This amounts to an increase of 2 bytes and a decrease of 1 byte.
     * @return A pretty-printed string that describes the diff
     */
    override fun toString() =
        StringBuilder()
            .apply {
                if (added.isEmpty() && removed.isEmpty()) {
                    append(MESSAGE_NO_DIFF)
                    return toString()
                }
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
                if (addedBytes > 0) {
                    append("${DiffColor.ADD.color.code}+ $addedBytes bytes${DiffColor.CLR.color.code}")
                    if (removedBytes > 0) {
                        append(", ")
                    }
                }

                if (removedBytes > 0) {
                    append("${DiffColor.DEL.color.code}- $removedBytes bytes${DiffColor.CLR.color.code}")
                }
            }.toString()

    /**
     * Plots the memory layout of the diff operations. This plots a graph showing where in the
     * memory heap operations were added or removed. The memory region is subdivided into a number of
     * <i>cells</i>. For each cell the first heap operation that was added or removed is shown.
     *
     * For example:\
     * `Address range: 6194336..1560286350`\
     * `005e84a0:       3+       .       .       .       .       .       .       .`\
     * `12e53b02:        .       .       .       .       .       .       .       .`\
     * `256bf164:        .       .       .       .       .       .       .       .`\
     * `37f2a7c6:        .       .       .       .       .       .       .       .`\
     * `4a795e28:        .       .       .       .       .       .       .      8+`\
     * `5d00148a:        .       .       .       .       .       .       .       .`
     *
     * @param dimensions The dimensions (columns and rows) indicating the number of cells in the
     * visual memory plot.
     * @return A pretty-printed string containing a plot of the changes in the memory layout.
     */
    fun plot(dimensions: TrackedHeap.PlotDimensions) =
        StringBuilder()
            .apply {
                val addedByAddress = added.groupBy { it.address }.toSortedMap()
                val removedByAddress = removed.groupBy { it.address }.toSortedMap()
                val addedAndRemovedByAddress = (added + removed).groupBy { it.address }.toSortedMap()
                if (addedAndRemovedByAddress.isEmpty()) {
                    return MESSAGE_NO_DIFF
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

        private fun inAddressRange(
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
                        "%${DEFAULT_PLOT_LAYOUT_COLUMN_WIDTH - 1}d",
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

                    repeat(DEFAULT_PLOT_LAYOUT_COLUMN_WIDTH - 1) {
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

        /**
         * Computes a diff on the specified trackedHeap. Diffs are computed on closed intervals of heap operation
         * sequence number ranges. The difference between the memory state before the first heap operation and the
         * memory state after the last heap operation is computed.
         *
         * @param trackedHeap The heap for a diff is computed.
         * @param diffSpec A diff-spec in the format of `x..y`, specifying a closed interval of heap operations.
         * The values x and y are sequence numbers of heap operations or named marker labels with an optional index.
         * The memory states before the operation x and after the operation y are used for the diff calculation.
         * A heap operation does not represent a single memory state but represents a change in memory state.
         * This means that a diff on diff-spec 0..0 always returns exactly operation 0 (the memory state changes
         * by the heap operation). Similarly, diff `0..n-1`, where n is the number of heap operations in the
         * tracked heap, returns the difference between the memory states when the capture started and when the capture
         * ended. The indices in the diff spec should be valid for the specified tracked heap.
         * @throws java.text.ParseException When the diff spec contains an invalid range or uses undefined markers.
         * @return A Diff object storing the memory heap differences computed for the specified tracked heap
         * and diff-spec.
         */
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
                    HeapOperation.Kind.Alloc -> {
                        added.add(it)
                    }

                    HeapOperation.Kind.Dealloc -> {
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
