package no.nordicsemi.android.nrfmesh.coldchain.v5.data.model

/**
 * 传感器报警上报消息 (OpCode 0xC3)
 * 包含完整 ECDSA 签名的报警数据
 */
data class AlarmReportMessage(
    val record: SensorRecordV2,
    val ecdsaSignature: ByteArray    // 64字节 ECDSA 签名
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AlarmReportMessage) return false
        return record == other.record &&
                ecdsaSignature.contentEquals(other.ecdsaSignature)
    }

    override fun hashCode(): Int {
        var result = record.hashCode()
        result = 31 * result + ecdsaSignature.contentHashCode()
        return result
    }
}
