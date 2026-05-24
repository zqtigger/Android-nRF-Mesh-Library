package no.nordicsemi.android.nrfmesh.coldchain.v5.data.model

/**
 * 网关节点状态信息
 */
data class GatewayStatus(
    val gwId: String,               // 网关标识
    val unicastAddr: Int,           // 单播地址（如 0x0001）
    val role: GatewayRole,          // Primary / Standby
    val isOnline: Boolean,
    val rssi: Int,                  // dBm
    val lastHeartbeat: Long,        // Unix 毫秒
    val relayCoverage: Float = 0f,  // Relay 覆盖率 0~1
    val firmwareVersion: String = ""
)

enum class GatewayRole {
    PRIMARY, STANDBY, RELAY, UNKNOWN
}
