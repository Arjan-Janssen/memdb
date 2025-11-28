package com.janssen.memdb

import memdb.Message
import java.io.File
import java.text.ParseException
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

const val MIN_GRAPH_COLUMNS = 8
const val MIN_GRAPH_ROWS = 0

@Suppress("TooManyFunctions")
data class TrackedHeap(
    val heapOperations: List<HeapOperation>,
    val markers: List<Marker>,
) {
    class Builder {
        val heapOperations = mutableListOf<HeapOperation>()
        val markers = mutableListOf<Marker>()

        fun addHeapOperation(builder: HeapOperation.Builder): Builder =
            apply {
                heapOperations.add(builder.build())
            }

        fun addHeapOperation(heapOperation: HeapOperation): Builder =
            apply {
                heapOperations.add(heapOperation)
            }

        fun addHeapOperations(heapOperations: List<HeapOperation>): Builder =
            apply {
                heapOperations.forEach { op ->
                    addHeapOperation(op)
                }
            }

        fun addSentinel(): Builder =
            apply {
                heapOperations.add(HeapOperation.Builder().sentinel().build())
            }

        fun addMarker(marker: Marker): Builder =
            apply {
                markers
                    .find {
                        it.name == marker.name && it.index == marker.index
                    }?.let {
                        throw IllegalArgumentException(
                            "Marker with name ${marker.name} and index ${marker.index} exists already",
                        )
                    }
                markers.add(marker)
            }

        fun addMarkers(markers: List<Marker>): Builder =
            apply {
                markers.forEach { marker ->
                    addMarker(marker)
                }
            }

        fun build(): TrackedHeap = TrackedHeap(heapOperations, markers)
    }

    private fun createIntRange(spec: String): IntRange {
        val fromToSpec = spec.split("..")
        if (fromToSpec.size != 2) {
            throw ParseException("Invalid range spec $spec. Expected format [from]..[to]", 0)
        }
        val fromPosition =
            fromPosition(fromToSpec[0])
                ?: throw ParseException("Invalid from-position in range spec $spec", 1)

        val toPosition =
            toPosition(fromToSpec[1])
                ?: throw ParseException("Invalid to-position in range spec $spec", 2)

        return IntRange(fromPosition, toPosition)
    }

    @ConsistentCopyVisibility
    data class Range private constructor(
        val trackedHeap: TrackedHeap,
        val range: IntRange,
    ) {
        companion object {
            fun wholeRangeInclusiveStr(trackedHeap: TrackedHeap) = "0..${trackedHeap.heapOperations.size - 1}"

            fun fromIntRange(
                trackedHeap: TrackedHeap,
                range: IntRange,
            ): Range {
                fun checkPositionValid(
                    position: Int?,
                    attributeName: String,
                ) {
                    position ?: throw ParseException("Invalid $attributeName in range", 1)

                    if (position < 0 ||
                        trackedHeap.heapOperations.size <= position
                    ) {
                        throw ParseException("Invalid $attributeName $position in range", 0)
                    }
                }

                checkPositionValid(range.first, "from-position")
                checkPositionValid(range.last, "to-position")
                return TrackedHeap.Range(trackedHeap, range)
            }

            fun fromString(
                trackedHeap: TrackedHeap,
                spec: String,
            ): Range {
                val fromToSpec = spec.split("..")
                if (fromToSpec.size != 2) {
                    throw ParseException("Invalid diff spec $spec. Expected format [from]..[to]", 0)
                }
                val range = trackedHeap.createIntRange(spec)
                return fromIntRange(trackedHeap, range)
            }
        }
    }

    fun markers(firstOperationSeqNo: Int): List<Marker> {
        val matches = mutableListOf<Marker>()
        markers.forEach {
            if (it.firstOperationSeqNo == firstOperationSeqNo) {
                matches.add(it)
            }
        }
        return matches
    }

    fun marker(
        name: String,
        index: Int,
    ): Marker? =
        markers
            .find {
                it.name == name && it.index == index
            }

    fun marker(name: String) = marker(name, 0)

    private fun position(
        positionSpec: String,
        isFrom: Boolean,
    ): Int? {
        fun parseMarkerSpec(spec: String): Pair<String, Int> {
            val splitString = spec.split(':')
            if (splitString.isEmpty()) {
                return Pair<String, Int>(spec, 0)
            }
            val name = splitString[0]
            val index =
                if (splitString.size > 1) {
                    splitString[1].toIntOrNull() ?: 0
                } else {
                    0
                }
            return Pair<String, Int>(name, index)
        }

        fun markerFromPosition(
            name: String,
            index: Int,
        ): Int? {
            marker(name, index)?.let {
                return it.firstOperationSeqNo
            }
            return null
        }

        fun markerToPosition(
            name: String,
            index: Int,
        ): Int? =
            markerFromPosition(name, index)?.also {
                return max(0, it - 1)
            }

        fun markerPosition(
            name: String,
            index: Int,
            isStart: Boolean,
        ) = if (isStart) {
            markerFromPosition(name, index)
        } else {
            markerToPosition(name, index)
        }

        val position = positionSpec.toIntOrNull()
        val (markerName, markerIndex) = parseMarkerSpec(positionSpec)
        return position ?: markerPosition(markerName, markerIndex, isFrom)
    }

    fun fromPosition(positionSpec: String) = position(positionSpec, true)

    fun toPosition(positionSpec: String) = position(positionSpec, false)

    private fun adjustSize(
        cumulativeSize: Int,
        heapOperation: HeapOperation,
    ) = if (heapOperation.kind == HeapOperation.Kind.Alloc) {
        cumulativeSize + heapOperation.size
    } else {
        cumulativeSize - heapOperation.size
    }

    private fun heapOperationsToString(heapOperations: List<HeapOperation>) =
        StringBuilder()
            .apply {
                append("heap operations:")
                var cumulativeSize = 0
                heapOperations.forEach {
                    cumulativeSize = adjustSize(cumulativeSize, it)
                    if (!it.sentinel()) {
                        append("\n  $it")
                        append(" -> $cumulativeSize")
                    }
                }
            }.toString()

    private fun markersToString(markers: List<Marker>) =
        StringBuilder()
            .apply {
                append("\n\nmarkers:")
                markers.forEach {
                    append("\n  $it")
                }
            }.toString()

    override fun toString() =
        StringBuilder()
            .apply {
                if (heapOperations.isNotEmpty()) {
                    append(heapOperationsToString(heapOperations))
                }

                if (markers.isNotEmpty()) {
                    append(markersToString(markers))
                }
            }.toString()

    private data class RowOperations(
        val seqNo: Int,
        val count: Int,
        val plotRange: IntRange,
    )

    private data class HeapSizeChange(
        val before: Int,
        val after: Int,
    )

    // Returns for each heap operation the cumulative heap sizes after the operation is
    // executed and, for deallocations, whether it matches its address with a previous alloc.
    // Unmatched deallocations are ignored in the cumulative heap size.
    // Matches are returned in the matches list.
    private data class HeapGraph(
        val sizeChanges: List<HeapSizeChange>,
    ) {
        companion object {
            fun compute(heapOperations: List<HeapOperation>): HeapGraph {
                val sizeChanges = mutableListOf<HeapSizeChange>()
                var currentHeapSize = 0
                heapOperations.forEach { op ->
                    val sizeChange =
                        if (op.kind == HeapOperation.Kind.Alloc) {
                            op.size
                        } else {
                            -op.size
                        }
                    sizeChanges.add(
                        HeapSizeChange(
                            currentHeapSize,
                            currentHeapSize + sizeChange,
                        ),
                    )
                    currentHeapSize += sizeChange
                }
                return HeapGraph(sizeChanges)
            }
        }
    }

    fun saveToFile(filePath: String) = toProtobuf().writeTo(File(filePath).outputStream())

    private fun plotGraphHeading(
        columns: Int,
        maxHeapSize: Int,
    ) = StringBuilder()
        .apply {
            require(MIN_GRAPH_COLUMNS <= columns)
            require(0 <= maxHeapSize)

            append(String.format(Locale.getDefault(), "%16s->", "allocated"))
            repeat(columns) {
                append(' ')
            }
            append("<-")
            append(maxHeapSize.toString())
        }.toString()

    private data class RowPlotSizes(
        val before: Int,
        val after: Int,
    )

    private data class RowPlotCharacters(
        val default: Char,
        val alloc: Char,
        val dealloc: Char,
    )

    private fun plotGraphColoredBar(
        color: AnsiColor,
        numCharacters: Int,
        plotCharacter: Char,
    ) = StringBuilder()
        .apply {
            require(0 <= numCharacters)

            if (numCharacters == 0) {
                return ""
            }
            append(color.code)
            repeat(numCharacters) {
                append(plotCharacter)
            }
            append(AnsiColor.RESET.code)
        }.toString()

    private fun plotGraphHeapOperationBar(
        rowPlotSizes: RowPlotSizes,
        plotCharacters: RowPlotCharacters,
    ): String {
        val sizeChange = rowPlotSizes.after - rowPlotSizes.before
        return StringBuilder()
            .apply {
                repeat(min(rowPlotSizes.before, rowPlotSizes.after)) {
                    append(plotCharacters.default)
                }
                append(
                    if (0 < sizeChange) {
                        plotGraphColoredBar(
                            DiffColor.ADD.color,
                            sizeChange,
                            plotCharacters.alloc,
                        )
                    } else {
                        plotGraphColoredBar(
                            DiffColor.DEL.color,
                            -sizeChange,
                            plotCharacters.dealloc,
                        )
                    },
                )
            }.toString()
    }

    private fun mangleMarkerName(
        name: String,
        index: Int,
    ) = if (index == 0) {
        String.format(
            Locale.getDefault(),
            "%s",
            name,
        )
    } else {
        String.format(
            Locale.getDefault(),
            "%s:%d",
            name,
            index,
        )
    }

    private fun plotGraphMarker(
        marker: Marker,
        columns: Int,
    ) = StringBuilder()
        .apply {
            require(MIN_GRAPH_COLUMNS <= columns)

            append(
                String.format(
                    Locale.getDefault(),
                    "%16s: ",
                    mangleMarkerName(marker.name, marker.index),
                ),
            )

            repeat(columns) {
                append('-')
            }
        }.toString()

    private fun plotGraphRow(
        rowOperations: RowOperations,
        columns: Int,
        rowPlotSizes: RowPlotSizes,
        plotCharacters: RowPlotCharacters,
    ) = StringBuilder()
        .apply {
            markers(rowOperations.seqNo).forEach {
                appendLine(plotGraphMarker(it, columns))
            }
            append(
                String.format(
                    Locale.getDefault(),
                    "%16d: ",
                    rowOperations.seqNo,
                ),
            )
            append(
                plotGraphHeapOperationBar(
                    rowPlotSizes,
                    plotCharacters,
                ),
            )
            appendLine()
            // plot markers associated with skipped heap operations
            val markerEndSeqNo =
                min(
                    rowOperations.seqNo + rowOperations.count - 1,
                    rowOperations.plotRange.last,
                )
            val markerRange = rowOperations.seqNo + 1..markerEndSeqNo
            for (skippedSeqNo in markerRange) {
                markers(skippedSeqNo).forEach {
                    appendLine(plotGraphMarker(it, columns))
                }
            }
        }.toString()

    private fun numPlotCharacters(
        columns: Int,
        size: Int,
        maxHeapSize: Int,
    ) = if (maxHeapSize > 0) ceil((size * columns) / maxHeapSize.toDouble()).toInt() else 0

    private fun numPlotCharacters(
        columns: Int,
        sizeChange: HeapSizeChange,
        maxHeapSize: Int,
    ) = RowPlotSizes(
        numPlotCharacters(columns, sizeChange.before, maxHeapSize),
        numPlotCharacters(columns, sizeChange.after, maxHeapSize),
    )

    private fun maxHeapSize(
        operationRange: IntRange,
        sizeChanges: List<HeapSizeChange>,
    ): Int {
        var maxHeapSize = 0
        sizeChanges.slice(operationRange).map {
            maxHeapSize = max(maxHeapSize, it.after)
        }
        return maxHeapSize
    }

    /**
     * Represents the dimensions of a textual graph plot, in columns and rows.
     *
     * @param columns Each column is a special character to use for plotting graphs, such as '#' character for a bar.
     * This field indicates the maximum number of such characters to plot in a graph bar. This is not the actual
     * maximum width of the graph, because labels are excluded.
     * @param rows Each row represents a line in the plot. The number of rows is just an indication of the number
     * of lines to use for plotting. The actual number may be adjusted so that each line shows a fixed integer
     * number of heap operations.
     */
    data class PlotDimensions(
        val columns: Int,
        val rows: Int,
    )

    /**
     * Plots a graph of the tracked heap to a string
     *
     * @param range The range of heap operations that should be plotted.
     * @param dimensions The dimensions of the plot. See @plotDimensions.
     * @return A string containing a visual graph plot of the tracked heap.
     */
    fun plotGraph(
        range: IntRange,
        dimensions: PlotDimensions,
    ) = StringBuilder()
        .apply {
            if (heapOperations.isEmpty()) {
                append(MESSAGE_NO_HEAP_OPERATIONS)
                return toString()
            }

            require(range.first >= 0)
            require(range.first <= range.last)
            require(dimensions.columns >= MIN_GRAPH_COLUMNS)
            require(dimensions.rows >= MIN_GRAPH_ROWS)

            val heapGraph = HeapGraph.compute(heapOperations)
            val maxHeapSize = maxHeapSize(range, heapGraph.sizeChanges)
            require(0 <= maxHeapSize)
            appendLine(plotGraphHeading(dimensions.columns, maxHeapSize))

            val numOperations = 1 + (range.last - range.first)
            val clampedRows = if (numOperations < dimensions.rows) numOperations else dimensions.rows
            if (clampedRows == 0) {
                return toString()
            }
            val operationsPerRow = ceil(numOperations.toDouble() / clampedRows).toInt()
            val plotCharacters =
                RowPlotCharacters(
                    '#',
                    '+',
                    '-',
                )
            for (rowSeqNo in range step operationsPerRow) {
                append(
                    plotGraphRow(
                        RowOperations(
                            rowSeqNo,
                            operationsPerRow,
                            range,
                        ),
                        dimensions.columns,
                        numPlotCharacters(
                            dimensions.columns,
                            heapGraph.sizeChanges[rowSeqNo],
                            maxHeapSize,
                        ),
                        plotCharacters,
                    ),
                )
            }

            // print potential terminating markers
            markers(range.last + 1).forEach {
                appendLine(plotGraphMarker(it, dimensions.columns))
            }
        }.toString()

    internal fun toProtobuf(): memdb.Message.Update =
        memdb.Message.Update
            .newBuilder()
            .apply {
                heapOperations.forEach {
                    addHeapOperations(HeapOperation.toProtobuf(it))
                }
                markers.forEach {
                    addMarkers(Marker.toProtobuf(it))
                }
            }.build()

    internal fun withoutUnmatchedDeallocs(): TrackedHeap {
        val allocsByAddress = mutableMapOf<Int, HeapOperation>()
        val validHeapOperations = mutableListOf<HeapOperation>()
        heapOperations.forEachIndexed { index, heapOperation ->
            when (heapOperation.kind) {
                HeapOperation.Kind.Alloc -> {
                    allocsByAddress[heapOperation.address] = heapOperation
                    validHeapOperations.add(heapOperation)
                }

                HeapOperation.Kind.Dealloc -> {
                    val matchingAlloc = allocsByAddress[heapOperation.address]
                    if (matchingAlloc != null) {
                        validHeapOperations.add(heapOperation.asMatched(matchingAlloc))
                        allocsByAddress.remove(matchingAlloc.address)
                    }
                }
            }
        }
        return TrackedHeap(validHeapOperations, markers)
    }

    /**
     * Selects the specified subrange of tracked heap operations from a tracked heap.
     *
     * @param range A closed range of heap operations that should be selected. The range should be ascending.
     * @return A new tracked heap object containing the specified range of heap operations.
     */
    fun select(range: Range): TrackedHeap = TrackedHeap(heapOperations.slice(range.range), markers)

    companion object {
        internal fun concatenate(trackedHeaps: List<TrackedHeap>) =
            Builder()
                .addHeapOperations(
                    trackedHeaps
                        .map {
                            it.heapOperations
                        }.flatten(),
                ).addMarkers(trackedHeaps.map { it.markers }.flatten())
                .build()

        /**
         * Loads a tracked heap from a file at the specified file path. The file should have been written using
         * saveToFile using the same version of the application (no versioning yet).
         *
         * @param filePath A path to a valid memdb file.
         * @return A tracked heap loaded from the file.
         */
        fun loadFromFile(filePath: String) =
            fromProtobuf(
                memdb.Message.Update.parseFrom(
                    File(
                        filePath,
                    ).inputStream(),
                ),
            )

        internal fun fromProtobuf(update: memdb.Message.Update): TrackedHeap {
            fun isSentinel(heapOperation: memdb.Message.HeapOperation) =
                heapOperation.kind == Message.HeapOperation.Kind.Alloc && heapOperation.size == 0L

            fun isValid(heapOperation: memdb.Message.HeapOperation) =
                heapOperation.kind != Message.HeapOperation.Kind.UNRECOGNIZED && !isSentinel(heapOperation)

            val validProtoHeapOperations =
                update.heapOperationsList.filter {
                    isValid(it)
                }
            val heapOperations =
                validProtoHeapOperations.mapIndexed { seqNo, heapOperation ->
                    HeapOperation.fromProtobuf(seqNo, heapOperation)
                }
            val markers = update.markersList.map(Marker::fromProtobuf)

            return TrackedHeap(heapOperations, markers)
        }
    }
}
