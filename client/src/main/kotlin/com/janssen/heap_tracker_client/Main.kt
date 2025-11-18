package com.janssen.heap_tracker_client

import java.net.ConnectException
import kotlinx.cli.*

val DEFAULT_CONNECTION_PORT = 8989

fun doCapture(connectionString : String) : TrackedHeap? {
    val splitConnectionString = connectionString.split(":")
    val hostName = if (splitConnectionString.isNotEmpty()) splitConnectionString[0] else "localhost"
    val port = if (splitConnectionString.size > 1) splitConnectionString[1].toInt() else DEFAULT_CONNECTION_PORT
    println("Capturing heap trace from $hostName:$port...")

    try {
        val client = Client();
        return client.capture(hostName, port)
    }
    catch (e: ConnectException) {
        println("Unable to connect to heap-tracker server: ${e.message}")
    }
    catch (e: Exception) {
        println("Exception: ${e.message}")
        e.printStackTrace()
    }

    return null
}

fun findPosition(trackedHeap: TrackedHeap, markerOrIndex: String) : Long? {
    val marker = trackedHeap.markers.find {
        it.name == markerOrIndex
    }
    marker?.let {
        return markerOrIndex.toLongOrNull();
    }
    return null;
}

fun print(diff: Diff) {
    println("Added:")
    diff.added.forEach {
        println(it)
    }

    println("Removed:")
    diff.removed.forEach {
        println(it)
    }
}

fun parseDiffSpec(trackedHeap: TrackedHeap, diffSpec: String) : TrackedHeap.DiffSpec? {
    val fromToSpec = diffSpec.split("..");
    if (fromToSpec.size != 2) {
        println("Invalid diff spec ${diffSpec}. Expected format [from]..[to]")
        return null;
    }

    val fromPosition = findPosition(trackedHeap, fromToSpec[0])
    if (fromPosition == null) {
        println("Invalid from position in diff spec ${diffSpec}. Unable to compute diff")
        return null;
    }
    val toPosition = findPosition(trackedHeap, fromToSpec[1])
    if (toPosition == null) {
        println("Invalid to position in diff spec ${diffSpec}. Unable to compute diff")
        return null;
    }
    return TrackedHeap.DiffSpec(trackedHeap, fromPosition!!, toPosition!!)
}

fun doDiff(trackedHeap: TrackedHeap, diffSpec : String) {
    val diffSpec = parseDiffSpec(trackedHeap, diffSpec)
    diffSpec?.let {
        val diff = Diff.compute(it)
        print(diff)
    }
}

fun doSave(trackedHeap: TrackedHeap, filePath: String) {
    TrackedHeap.saveToFile(trackedHeap, filePath);
}

fun print(histogram: Histogram) {
    println("Size:frequency histogram:")
    histogram.frequencyMap.forEach {
        val formattedSize = String.format("%10d", it.key);
        println("${formattedSize}\t${it.value}")
    }
}

fun runInteractiveMode() {
}

@OptIn(ExperimentalCli::class)
fun main(args: Array<String>) {
    println("Heap tracker (c) 2025 by Arjan Janssen")

    val parser = ArgParser("heap-tracker")
    val capture by parser.option (ArgType.String, shortName = "c", fullName = "capture", description = "Capture heap trace")
    val load by parser.option (ArgType.String, shortName = "l", fullName = "load", description = "Load stored heap trace")
    val save by parser.option (ArgType.String, shortName = "s", fullName = "save", description = "Save stored heap trace")
    val histogram by parser.option(ArgType.Boolean, shortName = "hist", fullName = "histogram", description = "Histogram").default(false)
    val interactive by parser.option(ArgType.Boolean, shortName = "i", fullName = "interactive", description = "Interactive mode").default(false)
    val diff by parser.option(ArgType.String, shortName = "d",fullName = "diff", description = "Diff between two positions in the tracked heap")
    parser.parse(args)

    var trackedHeap : TrackedHeap? = null
    capture?.let {
        trackedHeap = doCapture(it)
    }
    load?.let {
        println("Loading tracked heap from $it...")
        val client = Client()
        trackedHeap = client.load(it);
    }

    if (trackedHeap != null) {
        trackedHeap.print()

        histogram?.let {
            println("Histogram heap trace from $it")
            val histogram = Histogram.build(trackedHeap)
            print(histogram)
        }

        save?.let {
            println("Saving tracked heap to $it...")
            TrackedHeap.saveToFile(trackedHeap, it)
        }

        diff?.let {
            doDiff(trackedHeap,it)
        }
    }

    if (interactive) {
        runInteractiveMode();
    }
}