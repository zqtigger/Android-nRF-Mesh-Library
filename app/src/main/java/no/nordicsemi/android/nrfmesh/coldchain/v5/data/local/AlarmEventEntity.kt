package no.nordicsemi.android.nrfmesh.coldchain.v5.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 告警事件 Room Entity
 */
@Entity(
    tableName = "alarm_events",
    indices = [
        Index(value = ["nodeAddr"]),
        Index(value = ["timestamp"]),
        Index(value = ["acknowledged"])
    ]
)
data class AlarmEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nodeAddr: Int,
    val nodeName: String = "",
    val alarmType: String,          // AlarmType.name
    val value: Float,
    val threshold: Float,
    val durationSeconds: Int = 0,
    val timestamp: Long,            // Unix 毫秒
    val acknowledged: Boolean = false,
    val ecdsaSignature: ByteArray? = null,
    val receivedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AlarmEventEntity) return false
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
