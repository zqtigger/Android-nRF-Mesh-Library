package no.nordicsemi.android.nrfmesh.coldchain.v5.data.model

/**
 * 告警事件
 */
data class AlarmEvent(
    val id: Long = 0,
    val nodeAddr: Int,
    val nodeName: String,
    val alarmType: AlarmType,
    val value: Float,                   // 触发值
    val threshold: Float,               // 阈值
    val durationSeconds: Int = 0,       // 持续秒数
    val timestamp: Long,                // Unix 毫秒
    val acknowledged: Boolean = false,
    val ecdsaSignature: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AlarmEvent) return false
        return id == other.id &&
                nodeAddr == other.nodeAddr &&
                alarmType == other.alarmType &&
                timestamp == other.timestamp &&
                ecdsaSignature.contentEquals(other.ecdsaSignature)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + nodeAddr
        result = 31 * result + alarmType.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

enum class AlarmType(val label: String) {
    OVER_TEMP("超温"),
    UNDER_TEMP("低温"),
    OVER_HUMIDITY("湿度过高"),
    LOW_BATTERY("低电量"),
    OFFLINE("离线"),
    SENSOR_FAULT("传感器故障")
}
