package com.janssen.memdb

data class Marker(
    val firstOperationSeqNo: Int,
    val name: String,
) {
    override fun toString() = "marker[name: $name, seq-no: $firstOperationSeqNo]"

    companion object {
        fun fromProtobuf(proto: memdb.Message.Marker) =
            Marker(
                proto.firstOperationSeqNo.toInt(),
                proto.name,
            )

        fun toProtobuf(marker: Marker): memdb.Message.Marker =
            memdb.Message.Marker
                .newBuilder()
                .setName(marker.name)
                .setFirstOperationSeqNo(marker.firstOperationSeqNo.toLong())
                .build()
    }
}
