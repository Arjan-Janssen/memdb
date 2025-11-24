package com.janssen.memdb

import java.text.ParseException

@Suppress("TooManyFunctions")
class InteractiveMode(
    val memDB: MemDB,
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
        memDB.doLoad(requiredArg(args, 1, "file-path"))
    }

    private fun runSaveCommand(args: List<String>) {
        memDB.trackedHeap?.let {
            memDB.doSave(it, requiredArg(args, 1, "file-path"))
        }
    }

    private fun runPrintCommand(args: List<String>) {
        memDB.trackedHeap?.let {
            memDB.doPrint(
                it,
                requiredIntArg(args, 1, "heap-operation-sequence-number"),
                optionalArg(args, 2) in setOf("bt", "backtrace"),
            )
        }
    }

    private fun runDiffCommand(args: List<String>) {
        memDB.trackedHeap?.also {
            memDB.doDiff(it, requiredArg(args, 1, "diff-spec"))
        } ?: printNoTrackedHeap()
    }

    private fun runHistogramCommand(args: List<String>) {
        memDB.trackedHeap?.also {
            memDB.doHistogram(it, optionalArg(args, 1) !in setOf("no-buckets", "nb"))
        } ?: printNoTrackedHeap()
    }

    @Suppress("MagicNumber")
    private fun runPlotCommand(args: List<String>) {
        memDB.trackedHeap?.also {
            memDB.doPlot(
                it,
                optionalArg(args, 1) ?: TrackedHeap.Range.wholeRangeInclusiveStr(it),
                optionalIntArg(args, 2, "columns", DEFAULT_PLOT_COLUMNS),
                optionalIntArg(args, 3, "rows", DEFAULT_PLOT_COLUMNS),
            )
        } ?: printNoDiff()
    }

    private fun runPlotLayoutCommand(args: List<String>) {
        memDB.diff?.also {
            memDB.doPlotPlayout(
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
