package com.janssen.memdb

import memdb.Message
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

enum class HeapOperationKind {
    Alloc,
    Dealloc,
}

data class HeapOperation(
    val seqNo: Int,
    val kind: HeapOperationKind,
    val durationSinceServerStart: Duration,
    val address: Int,
    val size: Int,
    val threadId: Int,
    val backtrace: String,
) {
    class Builder(
        var seqNo: Int = 0,
    ) {
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
            address: Int,
            size: Int,
        ) = apply {
            this.kind = HeapOperationKind.Alloc
            this.address = address
            this.size = size
        }

        fun dealloc(address: Int) =
            apply {
                this.kind = HeapOperationKind.Dealloc
                this.address = address
                this.size = 0
            }

        fun threadId(threadId: Int) =
            apply {
                this.threadId = threadId
            }

        fun sinceServerStart(duration: Duration) =
            apply {
                this.durationSinceServerStart = duration
            }

        fun backtrace(backtrace: String) =
            apply {
                this.backtrace = backtrace
            }

        var kind = HeapOperationKind.Alloc
        var durationSinceServerStart = Duration.ZERO
        var address = 0
        var size = 0
        var threadId = 0
        var backtrace = ""
    }

    override fun toString() = toString(false)

    fun toString(showBacktrace: Boolean) =
        StringBuilder()
            .append(if (kind == HeapOperationKind.Alloc) "alloc[" else "dealloc[")
            .append("seq no: $seqNo, kind: $kind, duration: $durationSinceServerStart, ")
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
        fun fromProtobuf(
            seqNo: Int,
            proto: memdb.Message.HeapOperation,
        ): HeapOperation {
            val durationSinceServerStart = proto.microsSinceServerStart.toDuration(DurationUnit.MICROSECONDS)
            val heapOperationType =
                when (proto.kind) {
                    Message.HeapOperation.Kind.Alloc -> {
                        HeapOperationKind.Alloc
                    }

                    else -> {
                        HeapOperationKind.Dealloc
                    }
                }

            return HeapOperation(
                seqNo,
                heapOperationType,
                durationSinceServerStart,
                proto.address.toInt(),
                proto.size.toInt(),
                proto.threadId.toInt(),
                proto.backtrace,
            )
        }

        fun toProtobuf(heapOperation: HeapOperation): memdb.Message.HeapOperation =
            memdb.Message.HeapOperation
                .newBuilder()
                .setKind(
                    when (heapOperation.kind) {
                        HeapOperationKind.Alloc -> memdb.Message.HeapOperation.Kind.Alloc
                        else -> memdb.Message.HeapOperation.Kind.Dealloc
                    },
                ).setMicrosSinceServerStart(heapOperation.durationSinceServerStart.inWholeMicroseconds)
                .setAddress(heapOperation.address.toLong())
                .setSize(heapOperation.size.toLong())
                .setThreadId(heapOperation.threadId.toLong())
                .setBacktrace(heapOperation.backtrace)
                .build()
    }

    fun sentinel() = kind == HeapOperationKind.Alloc && size == 0
}
