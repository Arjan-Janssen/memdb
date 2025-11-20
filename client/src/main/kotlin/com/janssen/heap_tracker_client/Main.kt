package com.janssen.heap_tracker_client

import java.net.ConnectException
import kotlinx.cli.*
import java.util.EnumSet.range

val DEFAULT_CONNECTION_PORT = 8989
val DEFAULT_PLOT_COLUMNS = 80
val DEFAULT_PLOT_ROWS = 40

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

fun findPosition(trackedHeap: TrackedHeap, markerOrIndex: String) : Int? {
    val marker = trackedHeap.markers.find {
        it.name == markerOrIndex
    }
    marker?.let {
        return marker.firstOperationSeqNo
    }
    return markerOrIndex.toIntOrNull();
}

fun parseDiffSpec(trackedHeap: TrackedHeap, diffSpec: String) : TrackedHeap.DiffSpec? {
    val fromToSpec = diffSpec.split("..");
    if (fromToSpec.size != 2) {
        println("Invalid diff spec ${diffSpec}. Expected format [from]..[to]")
        return null;
    }

    val fromPosition = findPosition(trackedHeap, fromToSpec[0])
    if (fromPosition == null) {
        println("Invalid from position in diff spec ${diffSpec}.")
        return null;
    }
    val toPosition = findPosition(trackedHeap, fromToSpec[1])
    if (toPosition == null) {
        println("Invalid to position in diff spec ${diffSpec}.")
        return null;
    }
    return TrackedHeap.DiffSpec(trackedHeap, fromPosition!!, toPosition!!)
}

fun doDiff(trackedHeap: TrackedHeap, diffSpec : String) : Diff? {
    val diffSpec = parseDiffSpec(trackedHeap, diffSpec)
    diffSpec?.let {
        val diff = Diff.compute(it)
        println("diff from position ${diffSpec.from} to ${diffSpec.to}:\n${diff}")
        return diff
    }
    return null
}

fun doSave(trackedHeap: TrackedHeap, filePath: String) {
    TrackedHeap.saveToFile(trackedHeap, filePath);
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
    val backtrace by parser.option(ArgType.Int, shortName = "bt", fullName = "backtrace", description = "Shows a back trace for heap alloc with the specified sequence number")
    class Plot: Subcommand("plot", "Plot tracked heap") {
        val columns by option(ArgType.Int, "columns", "col", "Columns in characters").default(DEFAULT_PLOT_COLUMNS)
        val rows by option(ArgType.Int, "rows", shortName = "row", description = "Rows in characters").default(
            DEFAULT_PLOT_ROWS
        )
        val range by option(ArgType.String, "range", shortName = "r", description = "Limit plot to range")
        var enabled = false
        override fun execute() {
            enabled = true
        }
    }
    class PlotLayout : Subcommand("plot-layout", "Play memory layout") {
        val columns by option(ArgType.Int, "columns", "col", "Columns in characters").default(DEFAULT_PLOT_COLUMNS)
        val rows by option(ArgType.Int, "rows", shortName = "row", description = "Rows in characters").default(
            DEFAULT_PLOT_ROWS
        )
        var enabled = false
        override fun execute() {
            enabled = true
        }
    }
    val plot = Plot()
    val plotLayout = PlotLayout()
    parser.subcommands(plot, plotLayout)
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
        print(trackedHeap.toString())

        if (plot.enabled) {
            var fromPosition = 0
            var toPosition = trackedHeap.heapOperations.size
            plot.range?.let {
                val diffSpec = parseDiffSpec(trackedHeap, it)
                diffSpec?.from?.let {
                    fromPosition = it
                }
                diffSpec?.to?.let {
                    toPosition = it
                }
            }
            print(trackedHeap.toGraph(fromPosition, toPosition, plot.columns, plot.rows, '#'))
        }


        histogram?.let {
            if (it) {
                println("Histogram heap trace from $it")
                val histogram = Histogram.build(trackedHeap)
                print(histogram.toString())
            }
        }

        save?.let {
            println("Saving tracked heap to $it...")
            TrackedHeap.saveToFile(trackedHeap, it)
        }

        diff?.let {
            doDiff(trackedHeap,it)?.let {
                if (plotLayout.enabled) {
                    println("Plotting...")
                    print(it.plot(plotLayout.columns, plotLayout.rows))
                }
            }
        }

        backtrace?.let {
            if (it >= 0 && it < trackedHeap.heapOperations.size) {
                println("Backtrace:\n${trackedHeap.heapOperations[it].backtrace}")
            }
        }
    }

    if (interactive) {
        runInteractiveMode();
    }
}