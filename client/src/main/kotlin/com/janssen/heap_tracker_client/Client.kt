package com.janssen.heap_tracker_client

import java.lang.Thread.sleep
import java.net.InetSocketAddress
import java.net.Socket

class Client {
    fun load(filePath : String) : TrackedHeap {
        return TrackedHeap.loadFromFile(filePath);
    }

    fun capture(hostName : String, port : Int) : TrackedHeap{
        val socketAddress = InetSocketAddress(hostName, port)
        var socket = Socket()
        socket.connect(socketAddress)

        val trackedHeaps = mutableListOf<TrackedHeap>()
        while (!socket.isClosed) {
            sleep(100)
            val bytesAvailable = socket.inputStream.available()
            if (bytesAvailable >  0) {
                val bytesSent = socket.inputStream.readNBytes(bytesAvailable)
                val message = heap_tracker.Message.Update.parseFrom(bytesSent)
                trackedHeaps.add(TrackedHeap.fromProtobuf(message))
                if (message.endOfFile) {
                    println("End of file. Closing connection");
                    socket.close();
                }
            }
        }

        return TrackedHeap.concatenate(trackedHeaps);
    }
}
