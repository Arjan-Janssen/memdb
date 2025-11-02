package com.janssen.heap_tracker_client

import java.io.IOException
import java.lang.Thread.sleep
import java.net.InetSocketAddress
import java.net.Socket

class Client {
    constructor() {
        val socketAddress = InetSocketAddress("localhost", 8989)
        var socket = Socket()
        try {
            socket.connect(socketAddress)
        }
        catch (e: IOException) {
            println("Exception: ${e.message}")
            e.printStackTrace()
            return
        }

        var inputStream = socket.inputStream
        while (true) {
            sleep(500)
            val bytesAvailable = inputStream.available()
            if (bytesAvailable == 0) {
                continue
            }

            val bytesSent = inputStream.readNBytes(bytesAvailable)
            val heapOperations = protos.Message.HeapOperations.parseFrom(bytesSent)

            heapOperations.heapOperationList.forEach {
                println("Type: ${it.type}")
                println("Offset: ${it.address}")
                println("Size: ${it.size}")
            }

            println("Bytes received ${bytesSent.size} bytes!");
        }
    }
}

fun main() {
    var client = Client()
}