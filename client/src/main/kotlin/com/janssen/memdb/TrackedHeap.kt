package com.janssen.memdb

import heap_tracker.Message
import java.io.File
import java.text.ParseException
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min

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

    data class DiffSpec(
        val trackedHeap: TrackedHeap,
        val range: IntRange,
    ) {
        companion object {
            fun fromString(
                trackedHeap: TrackedHeap,
                specStr: String,
            ): DiffSpec {
                val fromToSpec = specStr.split("..")
                if (fromToSpec.size != 2) {
                    throw ParseException("Invalid diff spec $specStr. Expected format [from]..[to]", 0)
                }
                val fromPosition =
                    trackedHeap.markerPosition(fromToSpec[0])
                        ?: throw ParseException("Invalid from position in diff spec $specStr.", 0)
                val toPositionExclusive =
                    trackedHeap.markerPosition(fromToSpec[1])
                        ?: throw ParseException("Invalid to position in diff spec $specStr.", 1)
                val range = IntRange(fromPosition, (toPositionExclusive) - 1)
                return TrackedHeap.DiffSpec(trackedHeap, range)
            }
        }
    }

    fun marker(firstOperationSeqNo: Int): Marker? = markers.find { it.firstOperationSeqNo == firstOperationSeqNo }

    fun markerPosition(markerOrIndex: String): Int? {
        val marker =
            markers.find {
                it.name == markerOrIndex
            }
        marker?.let {
            return marker.firstOperationSeqNo
        }
        return markerOrIndex.toIntOrNull()
    }

    override fun toString(): String {
        var builder = StringBuilder().appendLine("tracked heap:")

        var cumulativeSize = 0
        heapOperations.forEach {
            builder.appendLine("$it")
            cumulativeSize += if (it.kind == HeapOperationKind.Alloc) it.size else -it.size
            builder.appendLine("cumulative size: $cumulativeSize")
        }

        println("markers:")
        markers.forEach {
            builder.appendLine("$it")
        }
        return builder.toString()
    }

    fun plotHeading(
        columns: Int,
        maxHeapSize: Int,
    ): String {
        val builder = StringBuilder()
        builder.append(String.format(Locale.getDefault(), "%10s->", "allocated"))
        repeat(columns) {
            builder.append(' ')
        }
        builder.append("<-")
        builder.append(maxHeapSize.toString())
        return builder.toString()
    }

    fun plotMarkerLine(
        name: String,
        columns: Int,
    ): String {
        val builder = StringBuilder()
        builder.append(String.format(Locale.getDefault(), "%10s: ", name))
        repeat(columns) {
            builder.append('-')
        }
        return builder.toString()
    }

    data class RowOperations(
        val seqNo: Int,
        val count: Int,
        val plotRange: IntRange,
    )

    fun plotGraphRow(
        rowOperations: RowOperations,
        columns: Int,
        numSymbols: Int,
        symbol: Char,
    ): String {
        val builder = StringBuilder()
        marker(rowOperations.seqNo)?.let {
            builder.appendLine(plotMarkerLine(it.name, columns))
        }

        builder.append(String.format(Locale.getDefault(), "%10d: ", rowOperations.seqNo))
        repeat(numSymbols) {
            builder.append(symbol)
        }
        builder.appendLine()

        val markerEndSeqNo = min(rowOperations.seqNo + rowOperations.count - 1, rowOperations.plotRange.last)
        val markerRange = rowOperations.seqNo + 1..markerEndSeqNo
        for (skippedSeqNo in markerRange) {
            marker(skippedSeqNo)?.let {
                builder.appendLine(plotMarkerLine(it.name, columns))
            }
        }

        return builder.toString()
    }

    data class PlotDimensions(
        val columns: Int,
        val rows: Int,
    )

    fun plotGraph(
        operationRange: IntRange,
        dimensions: PlotDimensions,
        symbol: Char,
    ): String {
        val heapSizes = mutableListOf<Int>()
        var currentHeapSize = 0
        heapOperations.forEach {
            currentHeapSize += if (it.kind == HeapOperationKind.Alloc) it.size else -it.size
            heapSizes.addLast(currentHeapSize)
        }
        var maxHeapSize = heapSizes.maxOrNull() ?: 0
        val builder = StringBuilder()
        builder.appendLine(plotHeading(dimensions.columns, maxHeapSize))

        val numOperations = 1 + (operationRange.endInclusive - operationRange.start)
        val clampedRows = if (numOperations < dimensions.rows) numOperations else dimensions.rows
        if (clampedRows == 0) {
            return builder.toString()
        }
        val operationsPerRow = ceil(numOperations.toDouble() / clampedRows).toInt()
        for (rowSeqNo in operationRange step operationsPerRow) {
            val numSymbols = (heapSizes[rowSeqNo] * dimensions.columns) / maxHeapSize
            builder.append(
                plotGraphRow(
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
            builder.appendLine(plotMarkerLine(it.name, dimensions.columns))
        }

        builder.appendLine()
        return builder.toString()
    }

    fun saveToFile(filePath: String) {
        val outputStream = File(filePath).outputStream()
        toProtobuf().writeTo(outputStream)
    }

    fun toProtobuf(): heap_tracker.Message.Update {
        val builder = heap_tracker.Message.Update.newBuilder()
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

        fun truncate(diffSpec: DiffSpec): TrackedHeap {
            val trackedHeap = diffSpec.trackedHeap
            val truncatedHeapOperations = trackedHeap.heapOperations.slice(diffSpec.range)
            return TrackedHeap(truncatedHeapOperations, trackedHeap.markers)
        }

        fun loadFromFile(filePath: String): TrackedHeap {
            val inputStream = File(filePath).inputStream()
            val proto = heap_tracker.Message.Update.parseFrom(inputStream)
            return fromProtobuf(proto)
        }

        fun fromProtobuf(update: heap_tracker.Message.Update): TrackedHeap {
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
