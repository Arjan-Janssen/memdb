package com.janssen.memdb

data class Marker(
    val firstOperationSeqNo: Int,
    val name: String,
) {
    companion object {
        fun fromProtobuf(proto: heap_tracker.Message.Marker) =
            Marker(
                proto.firstOperationSeqNo.toInt(),
                proto.name,
            )

        fun toProtobuf(marker: Marker): heap_tracker.Message.Marker =
            heap_tracker.Message.Marker
                .newBuilder()
                .setName(marker.name)
                .setFirstOperationSeqNo(marker.firstOperationSeqNo.toLong())
                .build()
    }
}
