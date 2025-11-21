package com.janssen.memdb

import heap_tracker.Message
import java.io.File
import java.text.ParseException
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

data class TrackedHeap(
    val heapOperations: List<HeapOperation>,
    val markers: List<Marker>,
) {
    enum class HeapOperationKind {
        Alloc,
        Dealloc,
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
                val fromPosition = trackedHeap.markerPosition(fromToSpec[0])
                if (fromPosition == null) {
                    throw ParseException("Invalid from position in diff spec $specStr.", 0)
                }
                val toPositionExclusive = trackedHeap.markerPosition(fromToSpec[1])
                if (toPositionExclusive == null) {
                    throw ParseException("Invalid to position in diff spec $specStr.", 1)
                }
                val range = IntRange(fromPosition!!, (toPositionExclusive!!) - 1)
                return TrackedHeap.DiffSpec(trackedHeap, range)
            }
        }
    }

    data class HeapOperation(
        val seqNo: Int,
        val kind: HeapOperationKind,
        val durationSinceServerStart: Duration,
        val address: Int,
        val size: Int,
        val threadId: Int,
        val backtrace: String,
    ) {
        override fun toString(): String =
            StringBuilder()
                .appendLine("heap operation[")
                .appendLine("seq no: $seqNo")
                .appendLine("kind: $kind")
                .appendLine("duration: $durationSinceServerStart")
                .appendLine(
                    String.format(
                        Locale.getDefault(),
                        "address: %s",
                        address.toHexString(),
                    ),
                ).appendLine("size:  $size")
                .appendLine("thread id: $threadId")
                .appendLine("backtrace: [not shown]")
                .appendLine("]")
                .toString()

        companion object {
            fun fromProtobuf(
                seqNo: Int,
                proto: heap_tracker.Message.HeapOperation,
            ): HeapOperation {
                val durationSinceServerStart = proto.microsSinceServerStart.toDuration(DurationUnit.MICROSECONDS)
                val heapOperationType =
                    when (proto.kind) {
                        Message.HeapOperation.Kind.Alloc -> {
                            HeapOperationKind.Alloc
                        }

                        else -> {
                            HeapOperationKind.Dealloc
                        }
                    }

                return HeapOperation(
                    seqNo,
                    heapOperationType,
                    durationSinceServerStart,
                    proto.address.toInt(),
                    proto.size.toInt(),
                    proto.threadId.toInt(),
                    proto.backtrace,
                )
            }

            fun toProtobuf(heapOperation: HeapOperation): heap_tracker.Message.HeapOperation =
                heap_tracker.Message.HeapOperation
                    .newBuilder()
                    .setKind(
                        when (heapOperation.kind) {
                            HeapOperationKind.Alloc -> heap_tracker.Message.HeapOperation.Kind.Alloc
                            else -> heap_tracker.Message.HeapOperation.Kind.Dealloc
                        },
                    ).setMicrosSinceServerStart(heapOperation.durationSinceServerStart.inWholeMicroseconds)
                    .setAddress(heapOperation.address.toLong())
                    .setSize(heapOperation.size.toLong())
                    .setThreadId(heapOperation.threadId.toLong())
                    .setBacktrace(heapOperation.backtrace)
                    .build()
        }
    }

    data class Marker(
        val firstOperationSeqNo: Int,
        val name: String,
    ) {
        companion object {
            fun fromProtobuf(proto: heap_tracker.Message.Marker) = Marker(proto.firstOperationSeqNo.toInt(), proto.name)

            fun toProtobuf(marker: Marker): heap_tracker.Message.Marker =
                heap_tracker.Message.Marker
                    .newBuilder()
                    .setName(marker.name)
                    .setFirstOperationSeqNo(marker.firstOperationSeqNo.toLong())
                    .build()
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

    fun plotGraphRow(
        rowSeqNo: Int,
        operationsPerRow: Int,
        columns: Int,
        numSymbols: Int,
        symbol: Char,
    ): String {
        val builder = StringBuilder()
        marker(rowSeqNo)?.let {
            builder.appendLine(plotMarkerLine(it.name, columns))
        }

        builder.append(String.format(Locale.getDefault(), "%10d: ", rowSeqNo))
        repeat(numSymbols) {
            builder.append(symbol)
        }

        for (skippedSeqNo in rowSeqNo + 1..<rowSeqNo + operationsPerRow) {
            marker(skippedSeqNo)?.let {
                builder.appendLine(plotMarkerLine(it.name, columns))
            }
        }

        return builder.toString()
    }

    fun plotGraph(
        range: IntRange,
        columns: Int,
        rows: Int,
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
        builder.appendLine(plotHeading(columns, maxHeapSize))
        val numOperations = 1 + (range.endInclusive - range.start)
        val operationsPerRow =
            if (numOperations <= rows) {
                1
            } else {
                Math.ceil(numOperations / rows.toDouble()).toInt()
            }
        for (rowSeqNo in range step operationsPerRow) {
            val numSymbols = (heapSizes[rowSeqNo] * columns) / maxHeapSize
            builder.appendLine(
                plotGraphRow(
                    rowSeqNo,
                    operationsPerRow,
                    columns,
                    numSymbols,
                    symbol,
                ),
            )
        }
        marker(range.endInclusive + 1)?.let {
            builder.appendLine(plotMarkerLine(it.name, columns))
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
            val heapOperations = mutableListOf<HeapOperation>()
            val markers = mutableListOf<Marker>()
            trackedHeaps.forEach {
                heapOperations.addAll(it.heapOperations)
                markers.addAll(it.markers)
            }
            return TrackedHeap(heapOperations, markers)
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
