package no.nordicsemi.android.nrfmesh.coldchain.v5.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.nordicsemi.android.mesh.MeshManagerApi
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode
import no.nordicsemi.android.nrfmesh.coldchain.ColdChainKeys
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.local.GatewayHeartbeatEntity
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.local.SensorRecordEntity
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.model.GatewayRole
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 已配网设备摘要（用于仪表盘显示）
 */
data class ProvisionedNodeInfo(
    val name: String,
    val unicastAddr: Int,
    val uuid: String,
    val role: String,       // "网关" / "传感器" / "Provisioner"
    val hasSensorData: Boolean = false,
    val lastTemp: Float? = null,
    val lastHumi: Float? = null,
    val batteryPct: Int = 0
)

/**
 * 仪表盘聚合数据
 */
data class DashboardSnapshot(
    val onlineSensorCount: Int = 0,
    val totalSensorCount: Int = 0,
    val gwCount: Int = 0,
    val sensorCount: Int = 0,
    val activeAlarmCount: Int = 0,
    val lowBatteryCount: Int = 0,
    val avgBatteryPct: Float = 0f,
    val primaryGatewayOnline: Boolean = false,
    val primaryGatewayRssi: Int = 0,
    val standbyGatewayOnline: Boolean = false,
    val relayCoverage: Float = 0f,
    val recentRecords: List<SensorRecordEntity> = emptyList(),
    val provisionedNodes: List<ProvisionedNodeInfo> = emptyList(),
    val lastUpdateTime: Long = System.currentTimeMillis()
)

/**
 * 仪表盘数据仓库
 * 聚合 Mesh 网络节点 + Room DB 传感器数据 + HTTP 网关状态
 */
@Singleton
class DashboardRepository @Inject constructor(
    private val meshDataRepository: MeshDataRepository,
    private val gatewayHttpRepository: GatewayHttpRepository,
    private val meshManagerApi: MeshManagerApi
) {
    private val _dashboardSnapshot = MutableStateFlow(DashboardSnapshot())
    val dashboardSnapshot: StateFlow<DashboardSnapshot> = _dashboardSnapshot.asStateFlow()

    suspend fun refreshDashboard() {
        try {
            val meshNet = meshManagerApi.meshNetwork
            val provisioned = meshNet?.nodes ?: emptyList()

            // 从 Room DB 获取最新采样记录
            val latestRecords = meshDataRepository.getLatestPerNode()
            val recordMap = latestRecords.associateBy { it.nodeAddr }

            // 构建节点信息（优先从 Mesh 网络，补充 Room DB 数据）
            val nodes = provisioned.map { node ->
                val rec = recordMap[node.unicastAddress]
                ProvisionedNodeInfo(
                    name = node.nodeName ?: "NODE_${Integer.toHexString(node.unicastAddress)}",
                    unicastAddr = node.unicastAddress,
                    uuid = node.uuid ?: "",
                    role = when {
                        ColdChainKeys.isGatewayAddr(node.unicastAddress) -> "网关"
                        ColdChainKeys.isSensorAddr(node.unicastAddress) -> "传感器"
                        else -> "设备"
                    },
                    hasSensorData = rec != null,
                    lastTemp = rec?.temp?.div(100f),
                    lastHumi = rec?.humi?.div(100f),
                    batteryPct = rec?.batteryPct ?: 0
                )
            }

            val onlineCount = provisioned.size
            val gwNodes = provisioned.count { ColdChainKeys.isGatewayAddr(it.unicastAddress) }
            val snNodes = provisioned.count { ColdChainKeys.isSensorAddr(it.unicastAddress) }
            val lowBattery = provisioned.count {
                recordMap[it.unicastAddress]?.batteryPct ?: 100 < 20
            }
            val avgBattery = if (recordMap.isNotEmpty())
                recordMap.values.map { it.batteryPct }.average().toFloat() else 0f

            val gateways = gatewayHttpRepository.getLatestGateways()
            val primary = gateways.find { it.role == GatewayRole.PRIMARY.name }
            val standby = gateways.find { it.role == GatewayRole.STANDBY.name }

            _dashboardSnapshot.value = DashboardSnapshot(
                onlineSensorCount = onlineCount,
                totalSensorCount = onlineCount,
                gwCount = gwNodes,
                sensorCount = snNodes,
                activeAlarmCount = 0,
                lowBatteryCount = lowBattery,
                avgBatteryPct = avgBattery,
                primaryGatewayOnline = primary?.isOnline ?: false,
                primaryGatewayRssi = primary?.rssi ?: 0,
                standbyGatewayOnline = standby?.isOnline ?: false,
                relayCoverage = primary?.relayCoverage ?: 0f,
                recentRecords = latestRecords.take(10),
                provisionedNodes = nodes,
                lastUpdateTime = System.currentTimeMillis()
            )
        } catch (_: Exception) { }
    }
}
