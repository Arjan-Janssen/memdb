package com.janssen.memdb

internal const val APP_NAME = "memdb"

fun main(args: Array<String>) {
    println("$APP_NAME (c) 2025 by Arjan Janssen")
    val memDB = MemDB()
    memDB.run(args)
}
