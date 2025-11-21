package com.janssen.memdb

import java.net.ConnectException
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.Subcommand
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.default

const val DEFAULT_CONNECTION_PORT = 8989
const val DEFAULT_PLOT_COLUMNS = 80
const val DEFAULT_PLOT_ROWS = 40

fun doCapture(connectionString: String): TrackedHeap? {
    val splitConnectionString = connectionString.split(":")
    val hostName = if (splitConnectionString.isNotEmpty()) splitConnectionString[0] else "localhost"
    val port = if (splitConnectionString.size > 1) splitConnectionString[1].toInt() else DEFAULT_CONNECTION_PORT
    println("Capturing heap trace from $hostName:$port...")

    try {
        val client = Client()
        return client.capture(hostName, port)
    }
    catch (e: ConnectException) {
        println("Unable to connect to heap-tracker server: ${e.message}")
    }

    return null
}

fun doLoad(filePath: String): TrackedHeap {
    println("Loading tracked heap from $filePath...")
    val client = Client()
    return client.load(filePath)
}

fun doDiff(trackedHeap: TrackedHeap, specStr : String) : Diff {
    val diffSpec = TrackedHeap.DiffSpec.fromString(trackedHeap, specStr)
    val diff = Diff.compute(diffSpec)
    println("diff from position ${diffSpec.range.start} to ${diffSpec.range.endInclusive}:\n${diff}")
    return diff
}

fun doPlot(trackedHeap: TrackedHeap,
           rangeSpec: String?,
           columns: Int,
           rows: Int) {
    val rangeSpec: TrackedHeap.DiffSpec = rangeSpec?.let {
        TrackedHeap.DiffSpec.fromString(trackedHeap, it)
    } ?: TrackedHeap.DiffSpec(trackedHeap, IntRange(0, trackedHeap.heapOperations.size - 1))
    println("Plot:")
    println(rangeSpec.trackedHeap.plotGraph(rangeSpec.range, columns, rows, '#'))
}

fun doTruncate(trackedHeap: TrackedHeap, rangeSpec: String): TrackedHeap {
    val diffSpec = TrackedHeap.DiffSpec.fromString(trackedHeap, rangeSpec)
    println("Truncating heap to ${diffSpec}...")
    return TrackedHeap.truncate(diffSpec)
}

fun doBacktrace(trackedHeap: TrackedHeap, seqNo: Int) {
    if (seqNo in 0..< trackedHeap.heapOperations.size) {
        println("Backtrace:")
        println(trackedHeap.heapOperations[seqNo].backtrace)
    }
    else {
        println("Invalid seqNo: $seqNo. Tracked heap size: ${trackedHeap.heapOperations.size}")
    }
}

fun doHistogram(trackedHeap: TrackedHeap) {
    println("Histogram:")
    println(Histogram.build(trackedHeap).toString())
}

fun doLayoutPlot(diff: Diff, columns: Int, rows: Int) {
    println("Layout plot:")
    println(diff.plot(columns, rows))
}

fun doSave(trackedHeap: TrackedHeap, filePath: String) {
    println("Saving tracked heap to $filePath...")
    TrackedHeap.saveToFile(trackedHeap, filePath)
}

@OptIn(ExperimentalCli::class)
class Plot: Subcommand("plot",
    "Plot tracked heap") {
    val columns by option(ArgType.Int,
        "columns",
        "col",
        "Columns in characters")
        .default(DEFAULT_PLOT_COLUMNS)
    val rows by option(ArgType.Int,
        "rows",
        shortName = "row",
        description = "Rows in characters")
        .default(DEFAULT_PLOT_ROWS
        )
    val range by option(ArgType.String,
        "range",
        shortName = "r",
        description = "Limit plot to range")
    var enabled = false
    override fun execute() {
        enabled = true
    }
}

@OptIn(ExperimentalCli::class)
class PlotLayout : Subcommand("plot-layout",
    "Play memory layout") {
    val columns by option(ArgType.Int,
        "columns",
        "col",
        "Columns in characters")
        .default(DEFAULT_PLOT_COLUMNS)
    val rows by option(ArgType.Int,
        "rows",
        shortName = "row",
        description = "Rows in characters")
        .default(DEFAULT_PLOT_ROWS
        )
    var enabled = false
    override fun execute() {
        enabled = true
    }
}

@OptIn(ExperimentalCli::class)
fun main(args: Array<String>) {
    println("memdb (c) 2025 by Arjan Janssen")

    val parser = ArgParser("heap-tracker")
    val capture by parser.option(ArgType.String,
                                 shortName = "c",
                                 fullName = "capture",
                                 description = "Capture heap trace")
    val load by parser.option(ArgType.String,
                              shortName = "l",
                              fullName = "load",
                              description = "Load stored heap trace")
    val save by parser.option(ArgType.String,
                              shortName = "s",
                              fullName = "save",
                              description = "Save stored heap trace")
    val histogram by parser.option(ArgType.Boolean,
                                   shortName = "hist",
                                   fullName = "histogram",
                                   description = "Histogram")
        .default(false)
    val diff by parser.option(ArgType.String,
                              shortName = "d",
                              fullName = "diff",
                              description = "Diff between two positions in the tracked heap")
    val truncate by parser.option(ArgType.String,
                                  shortName = "t",
                                  fullName = "truncate",
                                  description = "Truncate the tracked heap")
    val backtrace by parser.option(ArgType.Int,
                                   shortName = "bt",
                                   fullName = "backtrace",
                                   description = "Shows a back trace for heap alloc with the specified sequence number")

    val plot = Plot()
    val plotLayout = PlotLayout()
    parser.subcommands(plot, plotLayout)
    parser.parse(args)

    var trackedHeap : TrackedHeap? = null
    capture?.let {
        trackedHeap = doCapture(it)
    }
    load?.let {
        trackedHeap = doLoad(it)
    }
    if (trackedHeap == null) {
        return;
    }
    if (plot.enabled) {
        doPlot(trackedHeap, plot.range, plot.columns, plot.rows)
    }
    if (histogram) {
        doHistogram(trackedHeap)
    }
    diff?.let {
        val diffResult = doDiff(trackedHeap,it)
        if (plotLayout.enabled) {
            doLayoutPlot(diffResult, plotLayout.columns, plotLayout.rows)
        }
    }
    backtrace?.let {
        doBacktrace(trackedHeap, it)
    }
    var exportHeap = trackedHeap
    truncate?.let {
        exportHeap = doTruncate(trackedHeap, it);
    }
    exportHeap?.let { saveHeap ->
        save?.let { filePath ->
            doSave(saveHeap, filePath)
        }
    }
}
