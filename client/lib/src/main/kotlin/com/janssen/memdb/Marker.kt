package com.janssen.memdb

data class Marker(
    val firstOperationSeqNo: Int,
    val name: String,
    val index: Int = 0,
) {
    override fun toString() = "marker[name: $name, index: $index, seq-no: $firstOperationSeqNo]"

    companion object {
        internal fun fromProtobuf(proto: memdb.Message.Marker) =
            Marker(
                proto.firstOperationSeqNo.toInt(),
                proto.name,
                proto.index.toInt(),
            )

        internal fun toProtobuf(marker: Marker): memdb.Message.Marker =
            memdb.Message.Marker
                .newBuilder()
                .setName(marker.name)
                .setIndex(marker.index.toLong())
                .setFirstOperationSeqNo(marker.firstOperationSeqNo.toLong())
                .build()
    }
}
