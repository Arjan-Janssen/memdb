package com.janssen.memdb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class HeapOperationTest {
    @Test
    fun protobufRoundTripCommutes() {
        val seqNo = 10
        val heapOperation =
            HeapOperation
                .Builder(seqNo)
                .alloc(0, 1)
                .threadId(2)
                .sinceServerStart(100.toDuration(DurationUnit.MILLISECONDS))
                .backtrace("expected backtrace")
                .build()
        val otherOperation = HeapOperation.fromProtobuf(heapOperation.seqNo, HeapOperation.toProtobuf(heapOperation))
        assertEquals(heapOperation, otherOperation)
    }
}
