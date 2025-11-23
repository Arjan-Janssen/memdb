package com.janssen.memdb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MarkerTest {
    @Test
    fun `protobuf round-trip commutes`() {
        val expectedMarker = Marker(3, "label")
        assertEquals(
            expectedMarker,
            Marker.fromProtobuf(Marker.toProtobuf(expectedMarker)),
        )
    }

    @Test
    fun `toString returns readable string with all marker info`() {
        assertEquals(
            "marker[name: begin, seq-no: 2]",
            Marker(2, "begin").toString(),
        )
    }
}
