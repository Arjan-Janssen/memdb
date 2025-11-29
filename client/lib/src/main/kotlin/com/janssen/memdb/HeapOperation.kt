package com.janssen.memdb

import java.util.Locale
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Represents an operation on the memory heap. A heap operation can be an allocation or a deallocation.
 * @param seqNo A unique sequence number of the operation. Sequence numbers are zero-based and increasing
 * for each heap operation in a tracked heap.
 * @param kind The kind of heap operation: allocation or deallocation.
 * @param durationSinceServerStart This is the time since the moment when the memdb server tread started.
 * @param address The allocated address or the address that was deallocated.
 * @param size The size, in bytes, of the allocation. If a deallocation is matched to an allocation then
 * the size can be used to store the amount of memory that was deallocated.
 * @param threadId The thread id where the heap operation was executed.
 * @param backtracke a string storing a full backtrace of the function where the heap operation was executed.
 */
@ConsistentCopyVisibility
data class HeapOperation internal constructor(
    val seqNo: Int,
    val kind: Kind,
    val durationSinceServerStart: Duration,
    val address: Int,
    val size: Int,
    val threadId: Int,
    val backtrace: String,
) {
    /**
     * Indicates the kind of heap operation: allocation or deallocation.
     */
    enum class Kind {
        Alloc,
        Dealloc,
    }

    /**
     * Builder for creating heap operations. Offers a fluent-style interface to conveniently create
     * heap operations. Heap operation properties can be set using the member functions. The first step
     * is to call alloc or dealloc to set the type of heap operation. Next, additional properties
     * can be set, if needed. When all the properties are set, build can be called to create
     * an immutable heap operation.
     *
     * Example usage:
     * val allocation =
     *   HeapOperationBuilder()
     *    .alloc(address, size)
     *    .threadId(theThread)
     *    .duration(duration)
     *    .build()
     */
    internal class Builder(
        var seqNo: Int = 0,
    ) {
        /**
         * Builds a heap operation with the properties that were set on the builder.
         *
         * @return A heap operation with the properties set on the builder. By default, a
         * 0 byte alloc will be created at address 0.
         */
        fun build(): HeapOperation =
            HeapOperation(
                seqNo++,
                kind,
                durationSinceServerStart,
                address,
                size,
                threadId,
                backtrace,
            )

        fun alloc(
            newAddress: Int,
            newSize: Int,
        ) = apply {
            kind = Kind.Alloc
            address = newAddress
            size = newSize
        }

        fun dealloc(newAddress: Int) =
            apply {
                kind = Kind.Dealloc
                address = newAddress
                size = 0
            }

        fun size(newSize: Int) =
            apply {
                size = newSize
            }

        internal fun sentinel() =
            apply {
                kind = Kind.Alloc
                address = 0
                size = 0
            }

        fun threadId(newThreadId: Int) =
            apply {
                threadId = newThreadId
            }

        fun sinceServerStart(newDuration: Duration) =
            apply {
                durationSinceServerStart = newDuration
            }

        fun backtrace(newBacktrace: String) =
            apply {
                backtrace = newBacktrace
            }

        var kind = Kind.Alloc
        var durationSinceServerStart = Duration.ZERO
        var address = 0
        var size = 0
        var threadId = 0
        var backtrace = ""
    }

    /**
     * Pretty-prints the heap operation to a single line string.
     * For example:
     * `dealloc: seq no: 20, duration: 200ms, address: 00000002, size: 0, thread id: 4, backtrace: <hidden>`
     * The backtrace is by default not printed, because it can be large and may clutter the text.
     * To print the heap operation with a full backtrace call `toString(true)`.
     * @return A pretty-printed string that describes the heap operation.*
     */
    override fun toString() = toString(false)

    /**
     * Pretty-prints the heap operation with or without backtrace.
     *
     * @param showBacktrace Indicates whether to include the backtrace. @note Backtraces may contain many lines of text.
     * @return A pretty-printed string that describes the heap operation.
     */
    fun toString(showBacktrace: Boolean) =
        StringBuilder()
            .append(if (kind == Kind.Alloc) "alloc[" else "dealloc[")
            .append("seq no: $seqNo, duration: $durationSinceServerStart, ")
            .append(
                String.format(
                    Locale.getDefault(),
                    "address: %s, ",
                    address.toHexString(),
                ),
            ).append("size: $size, thread id: $threadId, ")
            .append("backtrace:${if (showBacktrace) "\n" + backtrace else " <hidden>"}")
            .append("]")
            .toString()

    companion object {
        internal fun fromProtobuf(
            seqNo: Int,
            proto: Message.HeapOperation,
        ) = HeapOperation(
            seqNo,
            when (proto.kind) {
                Message.HeapOperation.Kind.Alloc -> {
                    Kind.Alloc
                }

                Message.HeapOperation.Kind.Dealloc -> {
                    Kind.Dealloc
                }

                else -> {
                    throw IllegalArgumentException("Unexpected message kind ${proto.kind} for heap operation $proto")
                }
            },
            proto.microsSinceServerStart.toDuration(DurationUnit.MICROSECONDS),
            proto.address.toInt(),
            proto.size.toInt(),
            proto.threadId.toInt(),
            proto.backtrace,
        )

        internal fun toProtobuf(heapOperation: HeapOperation) =
            Message.HeapOperation
                .newBuilder()
                .setKind(
                    when (heapOperation.kind) {
                        HeapOperation.Kind.Alloc -> Message.HeapOperation.Kind.Alloc
                        else -> Message.HeapOperation.Kind.Dealloc
                    },
                ).setMicrosSinceServerStart(heapOperation.durationSinceServerStart.inWholeMicroseconds)
                .setAddress(heapOperation.address.toLong())
                .setSize(heapOperation.size.toLong())
                .setThreadId(heapOperation.threadId.toLong())
                .setBacktrace(heapOperation.backtrace)
                .build()
    }

    internal fun asMatched(alloc: HeapOperation) =
        HeapOperation(
            seqNo,
            kind,
            durationSinceServerStart,
            address,
            alloc.size,
            threadId,
            backtrace,
        )

    internal fun sentinel() = kind == Kind.Alloc && size == 0
}
