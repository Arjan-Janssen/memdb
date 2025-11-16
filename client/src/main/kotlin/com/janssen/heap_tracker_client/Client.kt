package com.janssen.heap_tracker_client

import java.lang.Thread.sleep
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket

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
                val message = heap_tracker.Message.Update.parseFrom(bytesSent)
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
