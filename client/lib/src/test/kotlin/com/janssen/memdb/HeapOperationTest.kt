package com.janssen.memdb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class HeapOperationBuilderTest {
    @Test
    fun `builder creates valid alloc operations`() {
        val alloc =
            HeapOperation(
                10,
                HeapOperationKind.Alloc,
                durationSinceServerStart = 200.toDuration(DurationUnit.MILLISECONDS),
                address = 2,
                size = 4,
                threadId = 5,
                backtrace = "alloc backtrace",
            )
        val builderAlloc =
            HeapOperation
                .Builder(alloc.seqNo)
                .alloc(alloc.address, alloc.size)
                .threadId(alloc.threadId)
                .sinceServerStart(alloc.durationSinceServerStart)
                .backtrace(alloc.backtrace)
                .build()
        assertEquals(alloc, builderAlloc)
    }

    @Test
    fun `builder creates valid dealloc operations`() {
        val seqNo = 10
        val dealloc =
            HeapOperation(
                10,
                HeapOperationKind.Dealloc,
                durationSinceServerStart = 500.toDuration(DurationUnit.MILLISECONDS),
                address = 3,
                size = 0,
                threadId = 6,
                backtrace = "dealloc backtrace",
            )
        val builderDealloc =
            HeapOperation
                .Builder(seqNo)
                .dealloc(dealloc.address)
                .threadId(dealloc.threadId)
                .sinceServerStart(dealloc.durationSinceServerStart)
                .backtrace(dealloc.backtrace)
                .build()
        assertEquals(dealloc, builderDealloc)
    }

    @Test
    fun `builder increments sequence-numbers`() {
        val seqNo = 10
        val builder = HeapOperation.Builder(seqNo)
        assertEquals(seqNo, builder.build().seqNo)
        assertEquals(seqNo + 1, builder.build().seqNo)
        assertEquals(seqNo + 2, builder.build().seqNo)
    }
}

class HeapOperationTest {
    @Test
    fun `protobuf round-trip commutes for alloc`() {
        val seqNo = 10
        val alloc =
            HeapOperation
                .Builder(seqNo)
                .alloc(1, 2)
                .threadId(3)
                .sinceServerStart(100.toDuration(DurationUnit.MILLISECONDS))
                .backtrace("expected backtrace")
                .build()
        val roundTripAlloc = HeapOperation.fromProtobuf(alloc.seqNo, HeapOperation.toProtobuf(alloc))
        assertEquals(alloc, roundTripAlloc)
    }

    @Test
    fun `protobuf round-trip commutes for dealloc`() {
        val seqNo = 20
        val dealloc =
            HeapOperation
                .Builder(seqNo)
                .dealloc(2)
                .threadId(4)
                .sinceServerStart(200.toDuration(DurationUnit.MILLISECONDS))
                .backtrace("expected backtrace2")
                .build()
        val roundTripDealloc = HeapOperation.fromProtobuf(dealloc.seqNo, HeapOperation.toProtobuf(dealloc))
        assertEquals(dealloc, roundTripDealloc)
    }

    @Test
    fun `toString is readable and contains all data for allocation`() {
        val expectedSeqNo = 26
        val alloc =
            HeapOperation
                .Builder(expectedSeqNo)
                .alloc(2, 16)
                .threadId(5)
                .sinceServerStart(300.toDuration(DurationUnit.MILLISECONDS))
                .backtrace("expected backtrace")
                .build()

        val expectedStringBacktrace =
            """alloc[seq no: 26, duration: 300ms, address: 00000002, size: 16, thread id: 5, backtrace:
expected backtrace]"""
        assertEquals(expectedStringBacktrace, alloc.toString(true))

        val expectedStringNoBacktrace =
            "alloc[seq no: 26, duration: 300ms, address: 00000002, size: 16, thread id: 5, backtrace: <hidden>]"
        assertEquals(expectedStringNoBacktrace, alloc.toString(false))
    }

    @Test
    fun `toString is readable and contains all data for deallocation`() {
        val expectedSeqNo = 20
        val dealloc =
            HeapOperation
                .Builder(expectedSeqNo)
                .dealloc(2)
                .threadId(4)
                .sinceServerStart(200.toDuration(DurationUnit.MILLISECONDS))
                .backtrace("expected backtrace")
                .build()

        val expectedStringBacktrace =
            "dealloc[seq no: 20, duration: 200ms, address: 00000002, size: 0, thread id: 4, " +
                "backtrace:\nexpected backtrace]"
        assertEquals(expectedStringBacktrace, dealloc.toString(true))

        val expectedStringNoBacktrace =
            "dealloc[seq no: 20, duration: 200ms, address: 00000002, size: 0, thread id: 4, backtrace: <hidden>]"
        assertEquals(expectedStringNoBacktrace, dealloc.toString(false))
    }
}
