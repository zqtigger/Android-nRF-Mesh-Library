package no.nordicsemi.android.nrfmesh.coldchain.v5.data.model

/**
 * V5 传感器采样记录（轻量单条数据）
 */
data class SensorRecordV2(
    val seqNum: Int,
    val timestamp: Long,          // Sensor RTC 时间（Unix 秒）
    val temp: Short,              // ×100
    val humi: Int,                // ×100
    val batteryPct: Int,
    val alarmFlag: Int,
    val hashPrev: ByteArray? = null  // 32字节 Hash Chain（可选）
) {
    /** 实际温度°C */
    val temperatureC: Float get() = temp / 100f
    /** 实际湿度%RH */
    val humidityRH: Float get() = humi / 100f

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SensorRecordV2) return false
        return seqNum == other.seqNum &&
                timestamp == other.timestamp &&
                temp == other.temp &&
                humi == other.humi &&
                batteryPct == other.batteryPct &&
                alarmFlag == other.alarmFlag &&
                hashPrev.contentEquals(other.hashPrev)
    }

    override fun hashCode(): Int {
        var result = seqNum
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + temp
        result = 31 * result + humi
        result = 31 * result + batteryPct
        result = 31 * result + alarmFlag
        result = 31 * result + (hashPrev?.contentHashCode() ?: 0)
        return result
    }
}
