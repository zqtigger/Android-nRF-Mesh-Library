package no.nordicsemi.android.nrfmesh.coldchain.v5.data.model

/**
 * V5 传感器节点扩展状态（聚合视图）
 */
data class SensorNodeState(
    val unicastAddr: Int,
    val nodeName: String,
    val uuid: String,
    val temperatureC: Float? = null,
    val humidityRH: Float? = null,
    val batteryPct: Int = 0,
    val rssi: Int = 0,
    val lastSampleTime: Long = 0,       // Unix 毫秒
    val cacheCount: Int = 0,            // 待上传缓存条数
    val regionGroup: Int = 0,           // 区域分组地址
    val isOnline: Boolean = false,
    val alarmActive: Boolean = false,
    val nodeRole: NodeRole = NodeRole.SENSOR
)

enum class NodeRole(val label: String) {
    SENSOR("传感器"),
    GATEWAY("网关"),
    RELAY("中继"),
    FRIEND("Friend")
}
