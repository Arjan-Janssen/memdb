package com.janssen.memdb

// Implementation based on
// https://stackoverflow.com/questions/77174295/peeking-into-an-iterator
interface PeekingIterator<T> : Iterator<T> {
    fun peek(): T
}

fun <T> Iterator<T>.peeking() = object : PeekingIterator<T> {
    private var hasPeeked = false
    private var peeked: T? = null

    override fun hasNext(): Boolean = hasPeeked || this@peeking.hasNext()

    override fun next(): T  {
        return if (hasPeeked) {
            hasPeeked = false
            @Suppress("UNCHECKED_CAST")
            peeked as T
        } else {
            this@peeking.next()
        }
    }

    override fun peek(): T {
        if (!hasPeeked) {
            peeked = this@peeking.next()
            hasPeeked = true
        }
        @Suppress("UNCHECKED_CAST")
        return peeked as T
    }
}
