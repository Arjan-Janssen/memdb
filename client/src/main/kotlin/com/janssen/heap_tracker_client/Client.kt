package com.janssen.heap_tracker_client

import java.lang.Thread.sleep
import java.net.InetSocketAddress
import java.net.Socket

class Client {
    fun load(filePath : String): TrackedHeap {
        return TrackedHeap.loadFromFile(filePath);
    }

    private fun pollMessage(socket: Socket): TrackedHeap?{
        val bytesAvailable = socket.inputStream.available()
        if (bytesAvailable ==  0) {
            return null;
        }
        val bytesSent = socket.inputStream.readNBytes(bytesAvailable)
        val message = heap_tracker.Message.Update.parseFrom(bytesSent)
        if (message.endOfFile) {
            println("End of file. Closing connection");
            socket.close();
        }
        return TrackedHeap.fromProtobuf(message)
    }

    fun capture(hostName : String, port : Int): TrackedHeap {
        val socketAddress = InetSocketAddress(hostName, port)
        var socket = Socket()
        socket.connect(socketAddress)

        val trackedHeaps = mutableListOf<TrackedHeap>()
        while (!socket.isClosed) {
            sleep(100)
            val trackedHeap = pollMessage(socket)
            trackedHeap?.let {
                trackedHeaps.add(it)
            }
        }

        return TrackedHeap.concatenate(trackedHeaps);
    }
}
