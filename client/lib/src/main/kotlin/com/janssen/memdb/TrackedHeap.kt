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

        fun addHeapOperation(builder: HeapOperation.Builder): Builder {
            heapOperations.add(builder.build())
            return this
        }

        fun addHeapOperation(heapOperation: HeapOperation): Builder {
            heapOperations.add(heapOperation)
            return this
        }

        fun addHeapOperations(heapOperations: List<HeapOperation>): Builder {
            heapOperations.forEach { op ->
                addHeapOperation(op)
            }
            return this
        }

        fun addMarker(marker: Marker): Builder {
            markers
                .find {
                    it.name == marker.name
                }?.let {
                    throw IllegalArgumentException("Marker with name ${marker.name} exists already")
                }

            markers.add(marker)
            return this
        }

        fun addMarkers(markers: List<Marker>): Builder {
            markers.forEach { marker ->
                addMarker(marker)
            }
            return this
        }

        fun build(): TrackedHeap = TrackedHeap(heapOperations, markers)
    }

    private fun createIntRange(
        spec: String,
        isDiff: Boolean,
    ): IntRange {
        val fromToSpec = spec.split("..")
        if (fromToSpec.size != 2) {
            throw ParseException("Invalid diff spec $spec. Expected format [from]..[to]", 0)
        }
        val fromPosition =
            rangeStartPosition(fromToSpec[0])
                ?: throw ParseException("Invalid from-position in diff spec $spec", 1)

        val toPosition =
            rangeEndPosition(fromToSpec[1], isDiff)
                ?: throw ParseException("Invalid to-position in diff spec $spec", 2)

        return IntRange(fromPosition, toPosition)
    }

    @ConsistentCopyVisibility
    data class DiffRange private constructor(
        val trackedHeap: TrackedHeap,
        val range: IntRange,
    ) {
        companion object {
            fun fromString(
                trackedHeap: TrackedHeap,
                spec: String,
            ): DiffRange {
                fun checkPositionValid(
                    position: Int,
                    attributeName: String,
                ) {
                    if (position < 0 ||
                        trackedHeap.heapOperations.size < position
                    ) {
                        throw ParseException("Invalid $attributeName $position in diff spec $spec", 0)
                    }
                }

                val range = trackedHeap.createIntRange(spec, true)
                checkPositionValid(range.first, "from-position")
                checkPositionValid(range.last, "to-position")
                return DiffRange(trackedHeap, range)
            }
        }
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
                val range = trackedHeap.createIntRange(spec, false)
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

    fun marker(markerName: String): Marker? =
        markers
            .find {
                it.name == markerName
            }

    fun rangeStartPosition(rangeSpecStart: String) =
        rangeSpecStart.toIntOrNull()
            ?: marker(rangeSpecStart)?.firstOperationSeqNo

    fun rangeEndPosition(
        rangeSpecEnd: String,
        isDiff: Boolean,
    ): Int? {
        fun markerEndPosition(markerName: String): Int? {
            marker(markerName)?.let {
                if (isDiff) {
                    return it.firstOperationSeqNo
                } else {
                    return it.firstOperationSeqNo - 1
                }
            }
            return null
        }

        val endPosition = rangeSpecEnd.toIntOrNull()
        return endPosition ?: markerEndPosition(rangeSpecEnd)
    }

    override fun toString(): String {
        var builder = StringBuilder()
        var indent = "  "

        if (heapOperations.isNotEmpty()) {
            builder.append("heap operations:")
            var cumulativeSize = 0
            heapOperations.forEach {
                builder.append("\n$indent$it")
                cumulativeSize += if (it.kind == HeapOperationKind.Alloc) it.size else -it.size
                builder.append(" -> $cumulativeSize")
            }
        }

        if (markers.isNotEmpty()) {
            builder.append("\n\nmarkers:")
            markers.forEach {
                builder.append("\n$indent$it")
            }
        }

        return builder.toString()
    }

    data class RowOperations(
        val seqNo: Int,
        val count: Int,
        val plotRange: IntRange,
    )

    data class PlotDimensions(
        val columns: Int,
        val rows: Int,
    )

    data class HeapSizeChange(
        val before: Int,
        val after: Int,
    )

    // Returns for each heap operation the cumulative heap sizes after the operation is
    // executed and, for deallocations, whether it matches its address with a previous alloc.
    // Unmatched deallocations are ignored in the cumulative heap size.
    // Matches are returned in the matches list.
    data class HeapGraph(
        val sizeChanges: List<HeapSizeChange>,
        val matchesAlloc: List<Boolean>,
    ) {
        companion object {
            fun compute(heapOperations: List<HeapOperation>): HeapGraph {
                val sizeChanges = mutableListOf<HeapSizeChange>()
                val matches = mutableListOf<Boolean>()
                var currentHeapSize = 0
                var sizeChange = 0
                val knownAllocs = mutableMapOf<Int, HeapOperation>()
                heapOperations.forEach { op ->
                    if (op.kind == HeapOperationKind.Alloc) {
                        sizeChange = op.size
                        knownAllocs[op.address] = op
                        // allocations always match
                        matches.add(true)
                    } else {
                        val alloc = knownAllocs[op.address]
                        alloc?.let { alloc ->
                            sizeChange = -alloc.size
                            knownAllocs.remove(alloc.address)
                        }
                        matches.add(alloc != null)
                    }
                    sizeChanges.add(
                        HeapSizeChange(
                            currentHeapSize,
                            currentHeapSize + sizeChange,
                        ),
                    )
                    currentHeapSize += sizeChange
                }
                return HeapGraph(sizeChanges, matches)
            }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    fun plotGraph(
        operationRange: IntRange,
        dimensions: PlotDimensions,
    ): String {
        fun plotHeading(
            columns: Int,
            maxHeapSize: Int,
        ): String {
            require(MIN_GRAPH_COLUMNS <= columns)
            require(0 <= maxHeapSize)

            val builder = StringBuilder()
            builder.append(String.format(Locale.getDefault(), "%10s->", "allocated"))
            repeat(columns) {
                builder.append(' ')
            }
            builder.append("<-")
            builder.append(maxHeapSize.toString())
            return builder.toString()
        }

        fun plotMarker(
            name: String,
            columns: Int,
        ): String {
            require(MIN_GRAPH_COLUMNS <= columns)

            val builder = StringBuilder()
            builder.append(String.format(Locale.getDefault(), "%10s: ", name))
            repeat(columns) {
                builder.append('-')
            }
            return builder.toString()
        }

        data class PlotSizes(
            val before: Int,
            val after: Int,
        )

        data class PlotSymbols(
            val default: Char,
            val alloc: Char,
            val dealloc: Char,
        )

        fun plotRow(
            rowOperations: RowOperations,
            columns: Int,
            plotSizes: PlotSizes,
            plotSymbols: PlotSymbols,
            matchesAlloc: Boolean,
        ): String {
            val builder = StringBuilder()
            markers(rowOperations.seqNo).forEach {
                builder.appendLine(plotMarker(it.name, columns))
            }

            if (!matchesAlloc) {
                builder.append(AnsiColor.RED.code)
            }
            builder.append(String.format(Locale.getDefault(), "%10d: ", rowOperations.seqNo))
            if (plotSizes.before < plotSizes.after) {
                repeat(plotSizes.before) {
                    builder.append(plotSymbols.default)
                }
                repeat(plotSizes.after - plotSizes.before) {
                    builder.append(plotSymbols.alloc)
                }
            } else {
                repeat(plotSizes.after) {
                    builder.append(plotSymbols.default)
                }
                repeat(plotSizes.before - plotSizes.after) {
                    builder.append(plotSymbols.dealloc)
                }
            }
            if (!matchesAlloc) {
                builder.append(AnsiColor.RESET.code)
            }
            builder.appendLine()

            // plot markers associated with skipped heap operations
            val markerEndSeqNo = min(rowOperations.seqNo + rowOperations.count - 1, rowOperations.plotRange.last)
            val markerRange = rowOperations.seqNo + 1..markerEndSeqNo
            for (skippedSeqNo in markerRange) {
                markers(skippedSeqNo).forEach {
                    builder.appendLine(plotMarker(it.name, columns))
                }
            }

            return builder.toString()
        }

        fun numSymbols(
            size: Int,
            maxHeapSize: Int,
            columns: Int,
        ) = if (maxHeapSize > 0) (size * dimensions.columns) / maxHeapSize else 0

        fun numSymbols(
            sizeChange: HeapSizeChange,
            maxHeapSize: Int,
            columns: Int,
        ) = PlotSizes(
            numSymbols(sizeChange.before, maxHeapSize, columns),
            numSymbols(sizeChange.after, maxHeapSize, columns),
        )

        fun maxHeapSize(sizeChanges: List<HeapSizeChange>): Int {
            var maxHeapSize = 0
            sizeChanges.slice(operationRange).forEach {
                maxHeapSize = max(maxHeapSize, it.after)
            }
            return maxHeapSize
        }

        require(0 <= operationRange.first)
        require(operationRange.first <= operationRange.last)
        require(operationRange.last < heapOperations.size)
        require(MIN_GRAPH_COLUMNS <= dimensions.columns)
        require(MIN_GRAPH_ROWS <= dimensions.rows)

        val heapGraph = HeapGraph.compute(heapOperations)
        var maxHeapSize = maxHeapSize(heapGraph.sizeChanges)
        require(0 <= maxHeapSize)

        val builder = StringBuilder()
        builder.appendLine(plotHeading(dimensions.columns, maxHeapSize))

        val numOperations = 1 + (operationRange.last - operationRange.first)
        val clampedRows = if (numOperations < dimensions.rows) numOperations else dimensions.rows
        if (clampedRows == 0) {
            return builder.toString()
        }
        val operationsPerRow = ceil(numOperations.toDouble() / clampedRows).toInt()
        val plotSymbols =
            PlotSymbols(
                '#',
                '>',
                '<',
            )
        for (rowSeqNo in operationRange step operationsPerRow) {
            val matchesAlloc = heapGraph.matchesAlloc[rowSeqNo]
            builder.append(
                plotRow(
                    RowOperations(
                        rowSeqNo,
                        operationsPerRow,
                        operationRange,
                    ),
                    dimensions.columns,
                    numSymbols(
                        heapGraph.sizeChanges[rowSeqNo],
                        maxHeapSize,
                        dimensions.columns,
                    ),
                    plotSymbols,
                    matchesAlloc,
                ),
            )
        }

        // print potential terminating markers
        markers(operationRange.last + 1).forEach {
            builder.appendLine(plotMarker(it.name, dimensions.columns))
        }

        return builder.toString()
    }

    fun saveToFile(filePath: String) {
        val outputStream = File(filePath).outputStream()
        toProtobuf().writeTo(outputStream)
    }

    fun toProtobuf(): memdb.Message.Update {
        val builder = memdb.Message.Update.newBuilder()
        heapOperations.forEach {
            builder.addHeapOperations(HeapOperation.toProtobuf(it))
        }
        markers.forEach {
            builder.addMarkers(Marker.toProtobuf(it))
        }
        return builder.build()
    }

    companion object {
        fun concatenate(trackedHeaps: List<TrackedHeap>): TrackedHeap {
            val builder = Builder()
            trackedHeaps.forEach {
                builder.addHeapOperations(it.heapOperations)
                builder.addMarkers(it.markers)
            }
            return builder.build()
        }

        fun truncate(range: Range): TrackedHeap {
            val trackedHeap = range.trackedHeap
            val truncatedHeapOperations = trackedHeap.heapOperations.slice(range.range)
            return TrackedHeap(truncatedHeapOperations, trackedHeap.markers)
        }

        fun loadFromFile(filePath: String): TrackedHeap {
            val inputStream = File(filePath).inputStream()
            val proto = memdb.Message.Update.parseFrom(inputStream)
            return fromProtobuf(proto)
        }

        fun fromProtobuf(update: memdb.Message.Update): TrackedHeap {
            val validProtoHeapOperations =
                update.heapOperationsList.filter {
                    it.kind != Message.HeapOperation.Kind.UNRECOGNIZED
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
