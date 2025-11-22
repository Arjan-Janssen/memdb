package com.janssen.memdb

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.UnknownHostException
import java.text.ParseException

const val DEFAULT_CONNECTION_PORT = 8989
const val DEFAULT_PLOT_COLUMNS = 80
const val DEFAULT_PLOT_ROWS = 40
const val DEFAULT_PLOT_LAYOUT_COLUMNS = 15
const val DEFAULT_PLOT_LAYOUT_ROWS = 40
const val APP_NAME = "memdb"

@Suppress("TooManyFunctions")
class HeapDB {
    @Suppress("LongMethod")
    @OptIn(ExperimentalCli::class)
    fun run(args: Array<String>) {
        val parser = ArgParser(APP_NAME)
        val captureOption by parser.option(
            ArgType.String,
            shortName = "c",
            fullName = "capture",
            description = "Capture heap trace",
        )
        val loadOption by parser.option(
            ArgType.String,
            shortName = "l",
            fullName = "load",
            description = "Load stored heap trace",
        )
        val saveOption by parser.option(
            ArgType.String,
            shortName = "s",
            fullName = "save",
            description = "Save stored heap trace",
        )
        val histogramOption by parser
            .option(
                ArgType.Boolean,
                shortName = "hist",
                fullName = "histogram",
                description = "Histogram",
            ).default(false)
        val noBucketsOption by parser
            .option(
                type = ArgType.Boolean,
                shortName = "nb",
                fullName = "no-buckets",
                description = "Disable power-of-two buckets for histogram",
            ).default(false)
        val diffOption by parser.option(
            ArgType.String,
            shortName = "d",
            fullName = "diff",
            description = "Diff between two positions in the tracked heap",
        )
        val truncateOption by parser.option(
            ArgType.String,
            shortName = "t",
            fullName = "truncate",
            description = "Truncate the tracked heap",
        )
        val printOption by parser.option(
            ArgType.Int,
            shortName = "p",
            fullName = "print",
            description = "Prints info about the heap operation with the specified sequence number",
        )
        val interactiveOption by parser
            .option(
                ArgType.Boolean,
                shortName = "i",
                fullName = "interactive",
                description = "Run in interactive mode",
            ).default(false)

        val plotCommand = PlotCommand()
        val plotLayoutCommand = PlotLayoutCommand()
        parser.subcommands(plotCommand, plotLayoutCommand)
        parser.parse(args)

        var trackedHeap: TrackedHeap? = null
        captureOption?.let {
            trackedHeap = doCapture(it)
        }
        loadOption?.let {
            trackedHeap = doLoad(it)
        }
        if (trackedHeap == null) {
            println("No tracked heap. Closing.")
            return
        }
        if (plotCommand.enabled) {
            doPlot(
                trackedHeap,
                plotCommand.range,
                plotCommand.columns,
                plotCommand.rows,
            )
        }
        if (plotLayoutCommand.enabled) {
            val diffSpec = diffOption ?: "0..${trackedHeap.heapOperations.size - 1}"
            doPlotPlayout(
                trackedHeap,
                diffSpec,
                plotLayoutCommand.columns,
                plotLayoutCommand.rows,
            )
        }
        if (histogramOption) {
            doHistogram(trackedHeap, !noBucketsOption)
        }
        var diff: Diff? = null
        diffOption?.let {
            diff = doDiff(trackedHeap, it)
        }
        printOption?.let {
            doPrint(trackedHeap, it, true)
        }
        var exportHeap = trackedHeap
        truncateOption?.let {
            exportHeap = doTruncate(trackedHeap, it)
        }
        exportHeap?.let { saveHeap ->
            saveOption?.let { filePath ->
                doSave(saveHeap, filePath)
            }
        }
        if (interactiveOption) {
            val interactiveMode = InteractiveMode(this, exportHeap, diff)
            interactiveMode.run()
        }
    }

    fun doCapture(connectionString: String): TrackedHeap? {
        fun parseConnectionString(connectionString: String): Pair<String, Int> {
            val splitConnectionString = connectionString.split(":")
            val hostName = if (splitConnectionString.isNotEmpty()) splitConnectionString[0] else "localhost"
            val port = if (splitConnectionString.size > 1) splitConnectionString[1].toInt() else DEFAULT_CONNECTION_PORT
            return Pair(hostName, port)
        }
        val (host, port) = parseConnectionString(connectionString)
        println("Capturing heap trace from $host:$port...")

        try {
            val client = Client()
            return client.capture(host, port)
        } catch (e: ConnectException) {
            println("Unable to connect to server. ${e.message}")
        } catch (e: UnknownHostException) {
            println("Unknown host: ${e.message}")
        }

        return null
    }

    fun doLoad(filePath: String): TrackedHeap? {
        println("Loading tracked heap from $filePath...")
        try {
            return TrackedHeap.loadFromFile(filePath)
        } catch (e: FileNotFoundException) {
            println("File not found. ${e.message}")
        }
        return null
    }

    fun doDiff(
        trackedHeap: TrackedHeap,
        specStr: String,
    ): Diff {
        val diffSpec = TrackedHeap.RangeSpec.fromString(trackedHeap, specStr)
        val diff = Diff.compute(diffSpec)
        println("diff from position ${diffSpec.range.start} to ${diffSpec.range.endInclusive}:\n$diff")
        return diff
    }

    fun doPlot(
        trackedHeap: TrackedHeap,
        rangeSpecStr: String?,
        columns: Int,
        rows: Int,
    ) {
        val rangeSpec: TrackedHeap.RangeSpec =
            rangeSpecStr?.let {
                TrackedHeap.RangeSpec.fromString(trackedHeap, it)
            } ?: TrackedHeap.RangeSpec(
                trackedHeap,
                IntRange(0, trackedHeap.heapOperations.size - 1),
            )

        println("Plot:")
        println(
            rangeSpec.trackedHeap.plotGraph(
                rangeSpec.range,
                TrackedHeap.PlotDimensions(columns, rows),
                '#',
            ),
        )
    }

    fun doTruncate(
        trackedHeap: TrackedHeap,
        rangeSpec: String,
    ): TrackedHeap {
        val diffSpec = TrackedHeap.RangeSpec.fromString(trackedHeap, rangeSpec)
        println("Truncating heap to $diffSpec...")
        return TrackedHeap.truncate(diffSpec)
    }

    fun doPrint(
        trackedHeap: TrackedHeap,
        seqNo: Int,
        printBacktrace: Boolean,
    ) {
        if (seqNo in 0..<trackedHeap.heapOperations.size) {
            println("Print:")
            println(trackedHeap.heapOperations[seqNo].toString(printBacktrace))
        } else {
            println("Invalid heap operation sequence number: $seqNo.")
            println("Tracked heap size: ${trackedHeap.heapOperations.size}")
        }
    }

    fun doHistogram(
        trackedHeap: TrackedHeap,
        buckets: Boolean,
    ) {
        println("Histogram:")
        println(Histogram.build(trackedHeap, buckets).toString())
    }

    fun doPlotPlayout(
        diff: Diff,
        columns: Int,
        rows: Int,
    ) {
        println("Layout plot:")
        println(diff.plot(TrackedHeap.PlotDimensions(columns, rows)))
    }

    fun doPlotPlayout(
        trackedHeap: TrackedHeap,
        diffSpecStr: String,
        columns: Int,
        rows: Int,
    ) {
        val diffSpec = TrackedHeap.RangeSpec.fromString(trackedHeap, diffSpecStr)
        val diff = Diff.compute(diffSpec)
        doPlotPlayout(diff, columns, rows)
    }

    fun doSave(
        trackedHeap: TrackedHeap,
        filePath: String,
    ) {
        println("Saving tracked heap to $filePath...")
        trackedHeap.saveToFile(filePath)
    }
}

@OptIn(ExperimentalCli::class)
class PlotCommand :
    Subcommand(
        "plot",
        "Plot tracked heap",
    ) {
    val columns by option(
        ArgType.Int,
        "columns",
        "col",
        "Columns in characters",
    ).default(DEFAULT_PLOT_COLUMNS)
    val rows by option(
        ArgType.Int,
        "rows",
        shortName = "row",
        description = "Rows in characters",
    ).default(
        DEFAULT_PLOT_ROWS,
    )
    val range by option(
        ArgType.String,
        "range",
        shortName = "r",
        description = "Limit plot to range",
    )
    var enabled = false

    override fun execute() {
        enabled = true
    }
}

@OptIn(ExperimentalCli::class)
class PlotLayoutCommand :
    Subcommand(
        "plot-layout",
        "Plot diff memory layout",
    ) {
    val columns by option(
        ArgType.Int,
        "columns",
        "col",
        "Columns in characters",
    ).default(DEFAULT_PLOT_LAYOUT_COLUMNS)
    val rows by option(
        ArgType.Int,
        "rows",
        shortName = "row",
        description = "Rows in characters",
    ).default(DEFAULT_PLOT_LAYOUT_ROWS)
    var enabled = false

    override fun execute() {
        enabled = true
    }
}

@Suppress("TooManyFunctions")
class InteractiveMode(
    val heapDB: HeapDB,
    var trackedHeap: TrackedHeap?,
    var diff: Diff?,
) {
    private fun printNoTrackedHeap() {
        println("No tracked heap available.")
    }

    private fun printNoDiff() {
        println("No diff available.")
    }

    private fun requiredArg(
        args: List<String>,
        position: Int,
        name: String,
    ): String {
        if (position >= args.size) {
            throw ParseException("Expected argument $name at position $position", position)
        }
        return args[position]
    }

    private fun requiredIntArg(
        args: List<String>,
        position: Int,
        name: String,
    ): Int {
        val arg = requiredArg(args, position, name)
        return arg.toIntOrNull()
            ?: throw ParseException("Expected integer argument $name at position $position", position)
    }

    private fun optionalArg(
        args: List<String>,
        position: Int,
    ): String? {
        if (position >= args.size) {
            return null
        }
        return args[position]
    }

    private fun optionalIntArg(
        args: List<String>,
        position: Int,
        name: String,
        defaultValue: Int,
    ): Int {
        val optionalArg = optionalArg(args, position)
        optionalArg ?: return defaultValue
        return optionalArg.toIntOrNull()
            ?: throw ParseException("Expected integer argument $name at position $position", position)
    }

    private fun runLoadCommand(args: List<String>) {
        trackedHeap = heapDB.doLoad(requiredArg(args, 1, "file-path"))
    }

    private fun runSaveCommand(args: List<String>) {
        trackedHeap = heapDB.doLoad(requiredArg(args, 1, "file-path"))
    }

    private fun runPrintCommand(args: List<String>) {
        trackedHeap?.also {
            heapDB.doPrint(it, requiredIntArg(args, 1, "heap-operation-sequence-number"), true)
        } ?: printNoTrackedHeap()
    }

    private fun runDiffCommand(args: List<String>) {
        trackedHeap?.also {
            diff = heapDB.doDiff(it, requiredArg(args, 1, "diff-spec"))
        } ?: printNoTrackedHeap()
    }

    @Suppress("MagicNumber")
    private fun runPlotCommand(args: List<String>) {
        trackedHeap?.also {
            heapDB.doPlot(
                it,
                requiredArg(args, 1, "range-spec"),
                optionalIntArg(args, 2, "columns", DEFAULT_PLOT_COLUMNS),
                optionalIntArg(args, 3, "rows", DEFAULT_PLOT_COLUMNS),
            )
        } ?: printNoDiff()
    }

    private fun runPlotLayoutCommand(args: List<String>) {
        diff?.let {
            heapDB.doPlotPlayout(
                it,
                optionalIntArg(args, 1, "columns", DEFAULT_PLOT_COLUMNS),
                optionalIntArg(args, 2, "rows", DEFAULT_PLOT_COLUMNS),
            )
        } ?: printNoDiff()
    }

    private fun parseAndRun(args: List<String>) {
        if (args.isEmpty()) {
            return
        }
        val command = args[0]
        try {
            when (command) {
                "q", "quit" -> shouldQuit = true
                "l", "load" -> runLoadCommand(args)
                "s", "save" -> runSaveCommand(args)
                "d", "diff" -> runDiffCommand(args)
                "p", "print" -> runPrintCommand(args)
                "plot" -> runPlotCommand(args)
                "plot-layout" -> runPlotLayoutCommand(args)
                else -> println("Unknown command: $command")
            }
        } catch (e: ParseException) {
            println("Invalid command. ${e.message}")
        }
    }

    fun run() {
        println("Interactive mode:")
        try {
            while (!shouldQuit) {
                print("> ")
                val args = readln().split(' ')
                parseAndRun(args)
            }
        } catch (e: ParseException) {
            println("$e")
        }
    }

    private var shouldQuit = false
}

fun main(args: Array<String>) {
    println("$APP_NAME (c) 2025 by Arjan Janssen")
    val heapDB = HeapDB()
    heapDB.run(args)
}
