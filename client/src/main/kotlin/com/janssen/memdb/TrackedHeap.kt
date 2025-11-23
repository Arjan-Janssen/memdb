package com.janssen.memdb

import memdb.Message
import java.io.File
import java.text.ParseException
import java.util.Locale
import kotlin.math.ceil
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

    fun marker(firstOperationSeqNo: Int): Marker? = markers.find { it.firstOperationSeqNo == firstOperationSeqNo }

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

        fun plotRow(
            rowOperations: RowOperations,
            columns: Int,
            numSymbols: Int,
            symbol: Char,
        ): String {
            val builder = StringBuilder()
            marker(rowOperations.seqNo)?.let {
                builder.appendLine(plotMarker(it.name, columns))
            }

            builder.append(String.format(Locale.getDefault(), "%10d: ", rowOperations.seqNo))
            repeat(numSymbols) {
                builder.append(symbol)
            }
            builder.appendLine()

            // plot markers associated with skipped heap operations
            val markerEndSeqNo = min(rowOperations.seqNo + rowOperations.count - 1, rowOperations.plotRange.last)
            val markerRange = rowOperations.seqNo + 1..markerEndSeqNo
            for (skippedSeqNo in markerRange) {
                marker(skippedSeqNo)?.let {
                    builder.appendLine(plotMarker(it.name, columns))
                }
            }

            return builder.toString()
        }

        require(0 <= operationRange.first)
        require(operationRange.first <= operationRange.last)
        require(operationRange.last < heapOperations.size)
        require(MIN_GRAPH_COLUMNS <= dimensions.columns)
        require(MIN_GRAPH_ROWS <= dimensions.rows)

        val symbol = '#'
        val postOperationHeapSizes = mutableListOf<Int>()
        var postOperationHeapSize = 0
        heapOperations.forEach {
            if (it.kind == HeapOperationKind.Alloc) {
                postOperationHeapSize += it.size
            } else {
                postOperationHeapSize -= it.size
            }
            postOperationHeapSizes.addLast(postOperationHeapSize)
        }
        val maxHeapSize = postOperationHeapSizes.maxOrNull() ?: 0
        val builder = StringBuilder()
        builder.appendLine(plotHeading(dimensions.columns, maxHeapSize))

        val numOperations = 1 + (operationRange.last - operationRange.first)
        val clampedRows = if (numOperations < dimensions.rows) numOperations else dimensions.rows
        if (clampedRows == 0) {
            return builder.toString()
        }
        val operationsPerRow = ceil(numOperations.toDouble() / clampedRows).toInt()
        for (rowSeqNo in operationRange step operationsPerRow) {
            val numSymbols = (postOperationHeapSizes[rowSeqNo] * dimensions.columns) / maxHeapSize
            builder.append(
                plotRow(
                    RowOperations(
                        rowSeqNo,
                        operationsPerRow,
                        operationRange,
                    ),
                    dimensions.columns,
                    numSymbols,
                    symbol,
                ),
            )
        }

        // print potential terminating markers
        marker(operationRange.last + 1)?.let {
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
