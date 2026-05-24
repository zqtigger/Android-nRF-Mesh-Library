package no.nordicsemi.android.nrfmesh.coldchain.v5.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.local.GatewayHeartbeatEntity
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.local.SensorRecordEntity
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.model.GatewayRole
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 仪表盘聚合数据
 */
data class DashboardSnapshot(
    val onlineSensorCount: Int = 0,
    val totalSensorCount: Int = 0,
    val activeAlarmCount: Int = 0,
    val lowBatteryCount: Int = 0,
    val avgBatteryPct: Float = 0f,
    val primaryGatewayOnline: Boolean = false,
    val primaryGatewayRssi: Int = 0,
    val standbyGatewayOnline: Boolean = false,
    val relayCoverage: Float = 0f,
    val recentRecords: List<SensorRecordEntity> = emptyList(),
    val lastUpdateTime: Long = System.currentTimeMillis()
)

/**
 * 仪表盘数据仓库
 * 聚合 Mesh + HTTP 数据，提供统一快照
 */
@Singleton
class DashboardRepository @Inject constructor(
    private val meshDataRepository: MeshDataRepository,
    private val gatewayHttpRepository: GatewayHttpRepository
) {
    /** 仪表盘数据快照 */
    private val _dashboardSnapshot = MutableStateFlow(DashboardSnapshot())
    val dashboardSnapshot: StateFlow<DashboardSnapshot> = _dashboardSnapshot.asStateFlow()

    /** 刷新仪表盘数据 */
    suspend fun refreshDashboard() {
        try {
            val latestRecords = meshDataRepository.getLatestPerNode()
            val onlineCount = latestRecords.size
            val lowBatteryCount = latestRecords.count { it.batteryPct < 20 }
            val avgBattery = if (latestRecords.isNotEmpty())
                latestRecords.map { it.batteryPct }.average().toFloat() else 0f

            // 网关状态
            val gateways = gatewayHttpRepository.getLatestGateways()
            val primary = gateways.find { it.role == GatewayRole.PRIMARY.name }
            val standby = gateways.find { it.role == GatewayRole.STANDBY.name }

            _dashboardSnapshot.value = DashboardSnapshot(
                onlineSensorCount = onlineCount,
                totalSensorCount = onlineCount,
                activeAlarmCount = 0, // 由 AlarmEventDao 单独提供
                lowBatteryCount = lowBatteryCount,
                avgBatteryPct = avgBattery,
                primaryGatewayOnline = primary?.isOnline ?: false,
                primaryGatewayRssi = primary?.rssi ?: 0,
                standbyGatewayOnline = standby?.isOnline ?: false,
                relayCoverage = primary?.relayCoverage ?: 0f,
                recentRecords = latestRecords.take(10),
                lastUpdateTime = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            // 保持上次快照
        }
    }
}
