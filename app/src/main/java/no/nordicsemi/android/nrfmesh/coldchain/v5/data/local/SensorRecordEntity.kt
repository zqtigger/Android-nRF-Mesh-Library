package no.nordicsemi.android.nrfmesh.coldchain.v5.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 传感器采样记录 Room Entity
 * 支持 V5 扩展字段（seqNum, hashPrev, alarmFlag）
 */
@Entity(
    tableName = "sensor_records",
    indices = [
        Index(value = ["nodeAddr"]),
        Index(value = ["timestamp"]),
        Index(value = ["nodeAddr", "timestamp"])
    ]
)
data class SensorRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nodeAddr: Int,              // 传感器 Unicast 地址
    val nodeName: String = "",
    val seqNum: Int,                // 序列号
    val timestamp: Long,            // Sensor RTC Unix 秒
    val receivedAt: Long,           // App 接收时间 Unix 毫秒
    val temp: Int,                  // ×100
    val humi: Int,                  // ×100
    val batteryPct: Int,
    val alarmFlag: Int,
    val rssi: Int = 0,
    val hashPrev: ByteArray? = null,// 32字节 Hash Chain
    val sourceType: String = "mesh" // "mesh" 或 "http"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SensorRecordEntity) return false
        return id == other.id &&
                nodeAddr == other.nodeAddr &&
                seqNum == other.seqNum &&
                timestamp == other.timestamp &&
                temp == other.temp &&
                hashPrev.contentEquals(other.hashPrev)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + nodeAddr
        result = 31 * result + seqNum
        result = 31 * result + timestamp.hashCode()
        return result
    }
}
