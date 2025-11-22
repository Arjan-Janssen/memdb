package com.janssen.memdb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class HeapOperationTest {
    class BuilderTest {
        @Test
        fun builderCreatesValidAlloc() {
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
        fun builderCreatesValidDealloc() {
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
        fun builderIncrementsSeqNo() {
            val seqNo = 10
            val builder = HeapOperation.Builder(seqNo)
            assertEquals(seqNo, builder.build().seqNo)
            assertEquals(seqNo + 1, builder.build().seqNo)
            assertEquals(seqNo + 2, builder.build().seqNo)
        }
    }

    @Test
    fun protobufAllocRoundTripCommutes() {
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
    fun protobufDeallocRoundTripCommutes() {
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
}
