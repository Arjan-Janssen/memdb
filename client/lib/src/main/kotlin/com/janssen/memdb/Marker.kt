package com.janssen.memdb

@ConsistentCopyVisibility
data class Marker internal constructor(
    val firstOperationSeqNo: Int,
    val name: String,
    val index: Int = 0,
) {
    /**
     * Converts the marker to a string. The string will be in the format:
     * marker[name: begin, index: 0, seq-no: 2]
     * In the example above, name specifies the name of the marker, here 'begin'. Index specifies
     * the index of the marker which is convenient when there are multiple markers with the same name.
     * Finally, seq-no represents the sequence number of the first heap operation just after the marker.
     * This heap operation will not exist if there are no heap operations after the final marker.
     */
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
