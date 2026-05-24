package no.nordicsemi.android.nrfmesh.coldchain.v5.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 网关心跳记录
 */
@Entity(
    tableName = "gateway_heartbeats",
    indices = [Index(value = ["gwId", "timestamp"])]
)
data class GatewayHeartbeatEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gwId: String,
    val unicastAddr: Int,
    val role: String,               // PRIMARY / STANDBY
    val isOnline: Boolean,
    val rssi: Int,
    val relayCoverage: Float,
    val timestamp: Long             // Unix 毫秒
)
