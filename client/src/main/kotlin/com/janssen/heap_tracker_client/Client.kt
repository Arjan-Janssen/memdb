package com.janssen.heap_tracker_client

import protos.Message
import java.lang.Thread.sleep
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class Heap {
    data class Alloc(val size: Long)

    fun alloc(address: Long, size: Long) {
        allocations[address] = Alloc(size)
    }

    fun free(address: Long) {
        allocations.remove(address)
    }

    val allocations = mutableMapOf<Long, Alloc>()
}
class TrackedHeap(val heapOperations : List<HeapOperation>, val markers : List<Marker>) {
    enum class HeapOperationType {
        Alloc,
        Free,
    }
    data class HeapOperation(val type: HeapOperationType, val durationSinceServerStart: Duration, val address: Long, val size: Long = 0)
    data class Marker(val operationSequenceNumber: Long, val name: String)

    fun print() {
        println("Heap operations:")
        heapOperations.forEach {
            println("${it}")
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

        fun fromProtobuf(message: protos.Message.HeapOperations) : TrackedHeap {
            val validProtoHeapOperations = message.heapOperationList.filter {
                it.type != Message.HeapOperations.HeapOperation.Type.UNRECOGNIZED
            }
            val heapOperations = validProtoHeapOperations.map {
                    val durationSinceServerStart = it.microsSinceServerStart.toDuration(DurationUnit.MICROSECONDS);
                    val heapOperationType =
                        when (it.type) {
                            Message.HeapOperations.HeapOperation.Type.TYPE_ALLOC ->
                                HeapOperationType.Alloc
                            else -> {
                                HeapOperationType.Free
                            }
                        }
                    TrackedHeap.HeapOperation(heapOperationType, durationSinceServerStart, it.address, it.size)
                }

            val markers = message.markerList.map {
                Marker(it.lastOperationSequenceNumber, it.name);
            }

            return TrackedHeap(heapOperations, markers)
        }
    }

}

class Client {
    constructor() {
        val socketAddress = InetSocketAddress("localhost", 8989)
        var socket = Socket()
        try {
            socket.connect(socketAddress)
        }
        catch (e: ConnectException) {
            println("Unable to connect to heap-tracker server at address $socketAddress: ${e.message}")
        }
        catch (e: Exception) {
            println("Exception: ${e.message}")
            e.printStackTrace()
            return
        }

        val trackedHeaps = mutableListOf<TrackedHeap>()
        while (!socket.isClosed) {
            sleep(100)
            val bytesAvailable = socket.inputStream.available()
            if (bytesAvailable >  0) {
                val bytesSent = socket.inputStream.readNBytes(bytesAvailable)
                val message = protos.Message.HeapOperations.parseFrom(bytesSent)
                trackedHeaps.add(TrackedHeap.fromProtobuf(message))
                if (message.endOfFile) {
                    println("End of file. Closing connection");
                    socket.close();
                }
            }
        }

        val trackedHeap = TrackedHeap.concatenate(trackedHeaps);
        trackedHeap.print()
    }
}

fun main() {
    println("Heap tracker (c) 2025 by Arjan Janssen")
    var client = Client()
}