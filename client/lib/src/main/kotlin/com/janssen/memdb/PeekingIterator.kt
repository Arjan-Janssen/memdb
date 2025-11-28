package com.janssen.memdb

// Implementation based on
// https://stackoverflow.com/questions/77174295/peeking-into-an-iterator
internal interface PeekingIterator<T> : Iterator<T> {
    fun peek(): T
}

internal fun <T> Iterator<T>.peeking(): PeekingIterator<T> =
    if (this is PeekingIterator) {
        this
    } else {
        object : PeekingIterator<T> {
            private var cached = false

            @Suppress("UNCHECKED_CAST")
            private var element: T = null as T
                get() {
                    if (!cached) {
                        field = this@peeking.next()
                    }
                    return field
                }

            override fun hasNext(): Boolean = cached || this@peeking.hasNext()

            override fun next(): T = element.also { cached = false }

            override fun peek(): T = element.also { cached = true }
        }
    }
