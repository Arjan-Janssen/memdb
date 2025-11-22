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
const val DEFAULT_PLOT_COLUMNS = 100
const val DEFAULT_PLOT_ROWS = 40
const val DEFAULT_PLOT_LAYOUT_COLUMNS = 15
const val DEFAULT_PLOT_LAYOUT_ROWS = 40
const val APP_NAME = "memdb"
const val NO_DIFF = "<no diff>"

enum class AnsiColor(
    val code: String,
) {
    RED("\u001b[31m"),
    GREEN("\u001b[32m"),
    RESET("\u001b[37m"),
    ;

    override fun toString(): String = code
}

class HeapDB(
    var trackedHeap: TrackedHeap? = null,
    var diff: Diff? = null,
) {
    @Suppress("LongMethod", "ComplexMethod")
    @OptIn(ExperimentalCli::class)
    fun run(args: Array<String>) {
        @OptIn(ExperimentalCli::class)
        class PlotCommand(
            val heapDB: HeapDB,
        ) : Subcommand(
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

            override fun execute() {
                heapDB.trackedHeap?.let {
                    heapDB.doPlot(
                        it,
                        range ?: TrackedHeap.Range.wholeRangeInclusiveStr(it),
                        columns,
                        rows,
                    )
                }
            }
        }

        @OptIn(ExperimentalCli::class)
        class PlotLayoutCommand(
            val heapDB: HeapDB,
        ) : Subcommand(
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

            override fun execute() {
                heapDB.diff?.let {
                    doPlotPlayout(
                        it,
                        columns,
                        rows,
                    )
                }
            }
        }

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

        val plotCommand = PlotCommand(this)
        val plotLayoutCommand = PlotLayoutCommand(this)
        parser.subcommands(plotCommand, plotLayoutCommand)
        parser.parse(args)

        captureOption?.let {
            doCapture(it)
        }
        loadOption?.let {
            doLoad(it)
        }
        if (trackedHeap == null) {
            println("No tracked heap. Closing.")
            return
        }
        if (histogramOption) {
            trackedHeap?.let {
                doHistogram(it, !noBucketsOption)
            }
        }
        diffOption?.let {
            trackedHeap?.let { heap ->
                doDiff(heap, it)
            }
        }
        printOption?.let {
            trackedHeap?.let { heap ->
                doPrint(heap, it, true)
            }
        }
        truncateOption?.let {
            trackedHeap?.let { heap ->
                doTruncate(heap, it)
            }
        }
        saveOption?.let { filePath ->
            trackedHeap?.let { heap ->
                doSave(heap, filePath)
            }
        }
        if (interactiveOption) {
            trackedHeap?.let { heap ->
                val interactiveMode = InteractiveMode(this)
                interactiveMode.run()
            }
        }
    }

    fun doCapture(connectionString: String) {
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
            trackedHeap = client.capture(host, port)
        } catch (e: ConnectException) {
            println("Unable to connect to server. ${e.message}")
        } catch (e: UnknownHostException) {
            println("Unknown host: ${e.message}")
        }
    }

    fun doLoad(filePath: String) {
        println("Loading tracked heap from $filePath...")
        try {
            trackedHeap = TrackedHeap.loadFromFile(filePath)
        } catch (e: FileNotFoundException) {
            println("File not found. ${e.message}")
        }
    }

    fun doDiff(
        trackedHeap: TrackedHeap,
        diffSpec: String,
    ) {
        diff = Diff.compute(trackedHeap, diffSpec)
        println("Diff:")
        println(diff.toString())
    }

    fun doPlot(
        trackedHeap: TrackedHeap,
        rangeSpecStr: String,
        columns: Int,
        rows: Int,
    ) {
        val range = TrackedHeap.Range.fromString(trackedHeap, rangeSpecStr)
        println("Plot:")
        println(
            range.trackedHeap.plotGraph(
                range.range,
                TrackedHeap.PlotDimensions(columns, rows),
                '#',
            ),
        )
    }

    fun doTruncate(
        trackedHeap: TrackedHeap,
        rangeSpec: String,
    ): TrackedHeap {
        val diffSpec = TrackedHeap.Range.fromString(trackedHeap, rangeSpec)
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

    fun doSave(
        trackedHeap: TrackedHeap,
        filePath: String,
    ) {
        println("Saving tracked heap to $filePath...")
        trackedHeap.saveToFile(filePath)
    }
}

@Suppress("TooManyFunctions")
class InteractiveMode(
    val heapDB: HeapDB,
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

    @Suppress("UnusedParameter")
    private fun runQuitCommand(args: List<String>) {
        shouldQuit = true
    }

    private fun runLoadCommand(args: List<String>) {
        heapDB.doLoad(requiredArg(args, 1, "file-path"))
    }

    private fun runSaveCommand(args: List<String>) {
        heapDB.trackedHeap?.let {
            heapDB.doSave(it, requiredArg(args, 1, "file-path"))
        }
    }

    private fun runPrintCommand(args: List<String>) {
        heapDB.trackedHeap?.let {
            heapDB.doPrint(
                it,
                requiredIntArg(args, 1, "heap-operation-sequence-number"),
                optionalArg(args, 2) in setOf("bt", "backtrace"),
            )
        }
    }

    private fun runDiffCommand(args: List<String>) {
        heapDB.trackedHeap?.also {
            heapDB.doDiff(it, requiredArg(args, 1, "diff-spec"))
        } ?: printNoTrackedHeap()
    }

    private fun runHistogramCommand(args: List<String>) {
        heapDB.trackedHeap?.also {
            heapDB.doHistogram(it, optionalArg(args, 1) !in setOf("no-buckets", "nb"))
        } ?: printNoTrackedHeap()
    }

    @Suppress("MagicNumber")
    private fun runPlotCommand(args: List<String>) {
        heapDB.trackedHeap?.also {
            heapDB.doPlot(
                it,
                optionalArg(args, 1) ?: TrackedHeap.Range.wholeRangeInclusiveStr(it),
                optionalIntArg(args, 2, "columns", DEFAULT_PLOT_COLUMNS),
                optionalIntArg(args, 3, "rows", DEFAULT_PLOT_COLUMNS),
            )
        } ?: printNoDiff()
    }

    private fun runPlotLayoutCommand(args: List<String>) {
        heapDB.diff?.also {
            heapDB.doPlotPlayout(
                it,
                optionalIntArg(args, 1, "columns", DEFAULT_PLOT_LAYOUT_COLUMNS),
                optionalIntArg(args, 2, "rows", DEFAULT_PLOT_LAYOUT_ROWS),
            )
        } ?: printNoDiff()
    }

    @Suppress("UnusedParameter")
    private fun runHelpCommand(args: List<String>) {
        println("Usage:")
        commands.forEach {
            println("${it.name}, ${it.shortName} -> ${it.help}")
        }
    }

    data class Command(
        val name: String,
        val shortName: String,
        val help: String,
        val run: (List<String>) -> Unit,
    )

    private val commands =
        listOf(
            Command(
                "quit",
                "q",
                "Quit application",
            ) {
                runQuitCommand(it)
            },
            Command(
                "load",
                "l",
                "Load tracked heap [file name]",
            ) {
                runLoadCommand(it)
            },
            Command(
                "save",
                "s",
                "Save tracked heap [file name]",
            ) {
                runSaveCommand(it)
            },
            Command(
                "print",
                "p",
                "Print heap operation [sequence number] ?[bt | backtrace]",
            ) {
                runPrintCommand(it)
            },
            Command(
                "plot",
                "plot",
                "Plot heap memory usage ?[range-spec] ?[columns] ?[rows]",
            ) {
                runPlotCommand(it)
            },
            Command(
                "plot-layout",
                "plot-layout",
                "Plot heap memory layout ?[columns] ?[rows]",
            ) {
                runPlotLayoutCommand(it)
            },
            Command(
                "diff",
                "d",
                "Diff between two pre-operation states of the heap [from-sequence-number..to-sequence-number]",
            ) {
                runDiffCommand(it)
            },
            Command(
                "histogram",
                "hist",
                "Print histogram of allocations by size ?[no-buckets | nb]",
            ) {
                runHistogramCommand(it)
            },
            Command(
                "help",
                "h",
                "Usage information",
            ) {
                runHelpCommand(it)
            },
        )

    private fun parseAndRun(args: List<String>) {
        if (args.isEmpty()) {
            return
        }
        val command = args[0]
        try {
            commands
                .find {
                    it.shortName == command || it.name == command
                }?.also {
                    it.run(args)
                } ?: println("Unknown command: $command")
        } catch (e: ParseException) {
            println("Invalid command. ${e.message}")
        }
    }

    fun run() {
        println("Interactive mode:")
        println("type h<enter> for help")
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
