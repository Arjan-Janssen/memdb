package com.janssen.memdb

import memdb.Message.HeapOperation.Kind
import java.lang.Thread.sleep
import java.net.InetSocketAddress
import java.net.Socket

internal const val SOCKET_POLL_WAIT_MILLIS = 100L

/**
 * Client can be used to connect to a memdb server and capture a tracked heap. The function
 * capture should be called to establish a connection and capture a tracked heap from the server.
 */
class Client {
    private fun pollMessage(socket: Socket): TrackedHeap? {
        fun isSentinel(heapOperation: memdb.Message.HeapOperation): Boolean =
            heapOperation.kind == Kind.Alloc && heapOperation.size.toInt() == 0

        val bytesAvailable = socket.inputStream.available()
        if (bytesAvailable == 0) {
            return null
        }
        val bytesSent = socket.inputStream.readNBytes(bytesAvailable)
        val message = memdb.Message.Update.parseFrom(bytesSent)
        if (message.heapOperationsList.isNotEmpty()) {
            val lastOperation = message.heapOperationsList.last()
            if (isSentinel(lastOperation)) {
                println("Last heap operation received. Closing connection")
                socket.close()
            }
        }
        return TrackedHeap.fromProtobuf(message)
    }

    /**
     * Connects to the memdb server to capture a <i>tracked heap</>. The function returns when the server
     * sends the final heap operation.
     *
     * @param hostName The name of the host to connect to. Can be localhost if the server runs locally.
     * @param port The port to connect to. The default port where the server port is 8989 but this can be configured.
     * @return The tracked heap that was collected from the server.
     */
    fun capture(
        hostName: String,
        port: Int,
    ): TrackedHeap {
        val socketAddress = InetSocketAddress(hostName, port)
        val socket = Socket()
        socket.connect(socketAddress)

        val trackedHeaps = mutableListOf<TrackedHeap>()
        while (!socket.isClosed) {
            sleep(SOCKET_POLL_WAIT_MILLIS)
            val trackedHeap = pollMessage(socket)
            trackedHeap?.let {
                trackedHeaps.add(it)
            }
        }

        return TrackedHeap
            .concatenate(trackedHeaps)
            .withoutUnmatchedDeallocs()
    }
}
