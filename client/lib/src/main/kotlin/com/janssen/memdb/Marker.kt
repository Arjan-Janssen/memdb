package com.janssen.memdb

/**
 * A marker is a named location in a tracked heap. The location of the marker is indicated by the sequence number
 * of the heap operation that follows it.
 *
 * There can be multiple markers with the same name. In this case, indices
 * are used to uniquely identify a marker.
 */
@ConsistentCopyVisibility
data class Marker internal constructor(
    val firstOperationSeqNo: Int,
    val name: String,
    val index: Int = 0,
) {
    /**
     * Pretty-prints the marker to a string in the format:
     * marker name: begin, index: 0, seq-no: 2
     * In the example above
     * - name specifies the name of the marker, here 'begin'.
     * - index specifies the index of the marker which is convenient when there are multiple markers with
     *   the same name.
     * - seq-no represents the sequence number of the first heap operation just after the marker.
     * This heap operation only exists if there are heap operations after the final marker.
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
