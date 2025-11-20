package com.janssen.memdb

import heap_tracker.Message
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

data class TrackedHeap(val heapOperations: List<HeapOperation>, val markers: List<Marker>) {
    enum class HeapOperationKind {
        Alloc,
        Dealloc,
    }

    data class DiffSpec(val trackedHeap: TrackedHeap, val range: IntRange)

    data class HeapOperation(val seqNo: Int,
                             val kind: HeapOperationKind,
                             val durationSinceServerStart: Duration,
                             val address: Int,
                             val size: Int,
                             val threadId: Int,
                             val backtrace: String) {
        override fun toString(): String {
            return StringBuilder()
                .appendLine("heap operation[")
                .appendLine("seq no: $seqNo")
                .appendLine("kind: $kind")
                .appendLine("duration: $durationSinceServerStart")
                .appendLine(String.format(Locale.getDefault(),
                                                 "address: %s",
                                                 address.toHexString()))
                .appendLine("size:  $size")
                .appendLine("thread id: $threadId")
                .appendLine("backtrace: [not shown]")
                .appendLine("]")
                .toString()
        }

        companion object {
            fun fromProtobuf(seqNo: Int, proto: heap_tracker.Message.HeapOperation): HeapOperation {
                val durationSinceServerStart = proto.microsSinceServerStart.toDuration(DurationUnit.MICROSECONDS)
                val heapOperationType =
                    when (proto.kind) {
                        Message.HeapOperation.Kind.Alloc ->
                            HeapOperationKind.Alloc
                        else -> {
                            HeapOperationKind.Dealloc
                        }
                    }

                return HeapOperation(seqNo,
                                     heapOperationType,
                                     durationSinceServerStart,
                                     proto.address.toInt(),
                                     proto.size.toInt(),
                                     proto.threadId.toInt(),
                                     proto.backtrace)
            }

            fun toProtobuf(heapOperation: HeapOperation): heap_tracker.Message.HeapOperation {
                return heap_tracker.Message.HeapOperation.newBuilder()
                        .setKind(when (heapOperation.kind) {
                            HeapOperationKind.Alloc -> heap_tracker.Message.HeapOperation.Kind.Alloc
                            else -> heap_tracker.Message.HeapOperation.Kind.Dealloc
                        })
                        .setMicrosSinceServerStart(heapOperation.durationSinceServerStart.inWholeMicroseconds)
                        .setAddress(heapOperation.address.toLong())
                        .setSize(heapOperation.size.toLong())
                        .setThreadId(heapOperation.threadId.toLong())
                        .setBacktrace(heapOperation.backtrace)
                        .build()
            }
        }
    }

    data class Marker(val firstOperationSeqNo: Int, val name: String) {
        companion object {
            fun fromProtobuf(proto: heap_tracker.Message.Marker) : Marker{
                return Marker(proto.firstOperationSeqNo.toInt(), proto.name)
            }

            fun toProtobuf(marker: Marker) : heap_tracker.Message.Marker {
                return heap_tracker.Message.Marker.newBuilder()
                    .setName(marker.name)
                    .setFirstOperationSeqNo(marker.firstOperationSeqNo.toLong())
                    .build()
            }
        }
    }

    fun markerPosition(markerOrIndex: String) : Int? {
        val marker = markers.find {
            it.name == markerOrIndex
        }
        marker?.let {
            return marker.firstOperationSeqNo
        }
        return markerOrIndex.toIntOrNull()
    }

    override fun toString(): String {
        var builder = StringBuilder().
            appendLine("tracked heap:")

        var cumulativeSize = 0
        heapOperations.forEach {
            builder.appendLine("${it}")
            cumulativeSize += if (it.kind == HeapOperationKind.Alloc) it.size else -it.size
            builder.appendLine("cumulative size: $cumulativeSize")
        }

        println("markers:")
        markers.forEach {
            builder.appendLine("${it}")
        }
        return builder.toString()
    }

    fun toGraph(range: IntRange, rows: Int, columns: Int, symbol: Char): String {
        val heapSizes = mutableListOf<Int>()
        var currentHeapSize = 0
        heapOperations.forEach {
            currentHeapSize += if (it.kind == HeapOperationKind.Alloc) it.size else -it.size
            heapSizes.addLast(currentHeapSize)
        }
        var maxHeapSize = 0
        heapSizes.forEach {
            if (it > maxHeapSize) {
                maxHeapSize = it
            }
        }

        val builder = StringBuilder()
            .appendLine("Graph: ")

        val numOperations = (range.endInclusive - range.start).toInt()
        val operationsPerLine = if (numOperations  <= rows) 1
                                else Math.ceil(numOperations  / rows.toDouble()).toInt()
        for (i in range step operationsPerLine) {
            val numSymbols = (heapSizes[i] * columns) / maxHeapSize
            builder.append(String.format(Locale.getDefault(), "%10d: ", i))
            for (i in 0 until numSymbols) {
                builder.append(symbol)
            }
            builder.appendLine()
        }

        builder.appendLine()
        return builder.toString()
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
            return TrackedHeap(trackedHeap.heapOperations.slice(diffSpec.range), trackedHeap.markers)
        }

        fun loadFromFile(filePath : String) : TrackedHeap {
            val inputStream = File(filePath).inputStream()
            val proto = heap_tracker.Message.Update.parseFrom(inputStream)
            return fromProtobuf(proto)
        }

        fun fromProtobuf(update: heap_tracker.Message.Update) : TrackedHeap {
            val validProtoHeapOperations = update.heapOperationsList.filter {
                it.kind != Message.HeapOperation.Kind.UNRECOGNIZED
            }
            val heapOperations = validProtoHeapOperations.mapIndexed { seqNo, heapOperation ->
                HeapOperation.fromProtobuf(seqNo, heapOperation)
            }
            val markers = update.markersList.map(Marker::fromProtobuf)

            return TrackedHeap(heapOperations, markers)
        }

        fun saveToFile(trackedHeap: TrackedHeap, filePath : String) {
            val outputStream = File(filePath).outputStream()
            TrackedHeap.toProtobuf(trackedHeap).writeTo(outputStream)
        }

        fun toProtobuf(trackedHeap: TrackedHeap) : heap_tracker.Message.Update {
            val builder = heap_tracker.Message.Update.newBuilder()

            trackedHeap.heapOperations.forEach {
                builder.addHeapOperations(HeapOperation.toProtobuf(it))
            }

            trackedHeap.markers.forEach {
                builder.addMarkers(Marker.toProtobuf(it))
            }

            return builder.build()
        }
    }
}
