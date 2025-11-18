package com.janssen.heap_tracker_client

import heap_tracker.Message
import java.io.File
import java.io.FileInputStream
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

data class TrackedHeap(val heapOperations: List<HeapOperation>, val markers: List<Marker>) {
    enum class HeapOperationKind {
        Alloc,
        Dealloc,
    }

    data class DiffSpec(val trackedHeap: TrackedHeap, val from: Long, val to: Long)

    data class HeapOperation(val seqNo: Long,
                             val kind: HeapOperationKind,
                             val durationSinceServerStart: Duration,
                             val address: Long,
                             val size: Long,
                             val thread_id: Long,
                             val backtrace: String) {
        override fun toString(): String {
            return StringBuilder()
                .appendLine("heap operation[")
                .appendLine("seq no: $seqNo")
                .appendLine("kind: $kind")
                .appendLine("duration: $durationSinceServerStart")
                .appendLine("address: $address")
                .appendLine("size:  $size")
                .appendLine("thread id: $thread_id")
                .appendLine("backtrace: [not shown]")
                .appendLine("]")
                .toString()
        }

        companion object {
            fun fromProtobuf(seqNo: Long, proto: heap_tracker.Message.HeapOperation): HeapOperation {
                val durationSinceServerStart = proto.microsSinceServerStart.toDuration(DurationUnit.MICROSECONDS);
                val heapOperationType =
                    when (proto.kind) {
                        Message.HeapOperation.Kind.Alloc ->
                            HeapOperationKind.Alloc
                        else -> {
                            HeapOperationKind.Dealloc
                        }
                    }

                return HeapOperation(seqNo, heapOperationType, durationSinceServerStart, proto.address, proto.size, proto.threadId, proto.backtrace)
            }

            fun toProtobuf(heapOperation: HeapOperation): heap_tracker.Message.HeapOperation {
                return heap_tracker.Message.HeapOperation.newBuilder()
                        .setKind(when (heapOperation.kind) {
                            HeapOperationKind.Alloc -> heap_tracker.Message.HeapOperation.Kind.Alloc
                            else -> heap_tracker.Message.HeapOperation.Kind.Dealloc
                        })
                        .setMicrosSinceServerStart(heapOperation.durationSinceServerStart.inWholeMicroseconds)
                        .setAddress(heapOperation.address)
                        .setSize(heapOperation.size)
                        .setThreadId(heapOperation.thread_id)
                        .setBacktrace(heapOperation.backtrace)
                        .build()
            }
        }
    }

    data class Marker(val operationSequenceNumber: Long, val name: String) {
        companion object {
            fun fromProtobuf(proto: heap_tracker.Message.Marker) : Marker{
                return Marker(proto.firstOperationSeqNo, proto.name);
            }

            fun toProtobuf(marker: Marker) : heap_tracker.Message.Marker {
                return heap_tracker.Message.Marker.newBuilder()
                    .setName(marker.name)
                    .setFirstOperationSeqNo(marker.operationSequenceNumber)
                    .build()
            }
        }
    }

    override fun toString(): String {
        var builder = StringBuilder().
            appendLine("tracked heap:")

        var cumulativeSize = 0L
        heapOperations.forEach {
            builder.appendLine("${it}")
            cumulativeSize += if (it.kind == HeapOperationKind.Alloc) it.size else -it.size;
            builder.appendLine("cumulative size: $cumulativeSize")
        }

        println("markers:")
        markers.forEach {
            builder.appendLine("${it}")
        }
        return builder.toString()
    }

    fun toGraph(maxSymbolsPerLine: Int, maxLines: Int, symbol: Char): String {
        val heapSizes = mutableListOf<Long>()
        var currentHeapSize = 0L;
        heapOperations.forEach {
            currentHeapSize += if (it.kind == HeapOperationKind.Alloc) it.size else -it.size
            heapSizes.addLast(currentHeapSize)
        }
        var maxHeapSize = 0L
        heapSizes.forEach {
            if (it > maxHeapSize) {
                maxHeapSize = it
            }
        }

        val builder = StringBuilder()
            .appendLine("Graph: ")

        val operationsPerLine  = if (heapOperations.size <= maxLines) 1 else Math.ceil(heapOperations.size / maxLines.toDouble()).toInt()
        for (i in 0..heapOperations.size-1 step operationsPerLine) {
            val numSymbols = (heapSizes[i] * maxSymbolsPerLine) / maxHeapSize
            builder.append(String.format("%10d: ", i))
            for (i in 0 until numSymbols) {
                builder.append(symbol)
            }
            builder.appendLine()
        }

        builder.appendLine()
        return builder.toString()
    }

    companion object {
        fun concatenate(trackedHeaps: List<TrackedHeap>) : TrackedHeap {
            val heapOperations = mutableListOf<HeapOperation>()
            val markers = mutableListOf<Marker>()
            trackedHeaps.forEach {
                heapOperations.addAll(it.heapOperations);
                markers.addAll(it.markers);
            }
            return TrackedHeap(heapOperations, markers)
        }

        fun loadFromFile(filePath : String) : TrackedHeap {
            val inputStream = File(filePath).inputStream()
            val proto = heap_tracker.Message.Update.parseFrom(inputStream)
            return fromProtobuf(proto);
        }

        fun fromProtobuf(update: heap_tracker.Message.Update) : TrackedHeap {
            val validProtoHeapOperations = update.heapOperationsList.filter {
                it.kind != Message.HeapOperation.Kind.UNRECOGNIZED
            }
            val heapOperations = validProtoHeapOperations.mapIndexed { seqNo, heapOperation ->
                HeapOperation.fromProtobuf(seqNo.toLong(), heapOperation)
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