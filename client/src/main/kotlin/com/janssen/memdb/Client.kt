package com.janssen.memdb

import java.lang.Thread.sleep
import java.net.InetSocketAddress
import java.net.Socket

const val SOCKET_POLL_WAIT_MILLIS = 100L

class Client {
    private fun pollMessage(socket: Socket): TrackedHeap? {
        val bytesAvailable = socket.inputStream.available()
        if (bytesAvailable == 0) {
            return null
        }
        val bytesSent = socket.inputStream.readNBytes(bytesAvailable)
        val message = memdb.Message.Update.parseFrom(bytesSent)
        if (message.endOfFile) {
            println("End of file. Closing connection")
            socket.close()
        }
        return TrackedHeap.fromProtobuf(message)
    }

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

        return TrackedHeap.concatenate(*trackedHeaps.toTypedArray())
    }
}
