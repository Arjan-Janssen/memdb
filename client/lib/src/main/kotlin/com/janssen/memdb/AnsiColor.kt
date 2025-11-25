package com.janssen.memdb

enum class AnsiColor(
    val code: String,
) {
    RED("\u001b[31m"),
    GREEN("\u001b[32m"),
    RESET("\u001b[0m"),
    ;

    override fun toString(): String = code
}

enum class DiffColor(
    val color: AnsiColor,
) {
    ADD(AnsiColor.GREEN),
    DEL(AnsiColor.RED),
    CLR(AnsiColor.RESET),
    ;

    override fun toString(): String = color.toString()
}
