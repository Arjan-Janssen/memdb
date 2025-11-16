package com.janssen.heap_tracker_client

import heap_tracker.Message
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class TrackedHeap(val heapOperations : List<HeapOperation>, val markers : List<Marker>) {
    inner class Diff(val fromInclusive: Long, val toExclusive: Long) {
    }

    enum class HeapOperationType {
        Alloc,
        Dealloc,
    }
    data class HeapOperation(val type: HeapOperationType,
                             val durationSinceServerStart: Duration,
                             val address: Long,
                             val size: Long,
                             val thread_id: Long,
                             val backtrace: String) {
        companion object {
            fun fromProtobuf(proto: heap_tracker.Message.HeapOperation) : HeapOperation {
                val durationSinceServerStart = proto.microsSinceServerStart.toDuration(DurationUnit.MICROSECONDS);
                val heapOperationType =
                    when (proto.kind) {
                        Message.HeapOperation.Kind.Alloc ->
                            HeapOperationType.Alloc
                        else -> {
                            HeapOperationType.Dealloc
                        }
                    }

                return HeapOperation(heapOperationType, durationSinceServerStart, proto.address, proto.size, proto.threadId, proto.backtrace)
            }
        }
    }
    data class Marker(val operationSequenceNumber: Long, val name: String) {
        companion object {
            fun fromProtobuf(proto: heap_tracker.Message.Marker) : Marker{
                return Marker(proto.firstOperationSeqNo, proto.name);
            }
        }
    }

    fun print() {
        println("${heapOperations.size} heap operations:")
        var cumulativeSize = 0L
        heapOperations.forEach {
            println("${it}")
            cumulativeSize += if (it.type == HeapOperationType.Alloc) it.size else -it.size;
            println("cumulative size: $cumulativeSize")
        }

        println("Markers:")
        markers.forEach {
            println("${it}")
        }
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

        fun fromProtobuf(update: heap_tracker.Message.Update) : TrackedHeap {
            val validProtoHeapOperations = update.heapOperationsList.filter {
                it.kind != Message.HeapOperation.Kind.UNRECOGNIZED
            }
            val heapOperations = validProtoHeapOperations.map(HeapOperation::fromProtobuf)
            val markers = update.markersList.map(Marker::fromProtobuf)

            return TrackedHeap(heapOperations, markers)
        }
    }
}