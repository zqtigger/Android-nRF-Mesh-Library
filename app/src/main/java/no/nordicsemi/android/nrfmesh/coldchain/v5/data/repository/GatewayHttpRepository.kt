package no.nordicsemi.android.nrfmesh.coldchain.v5.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.local.GatewayHeartbeatDao
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.local.GatewayHeartbeatEntity
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.local.SensorRecordDao
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.model.GatewayStatus
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.model.GatewayRole
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.model.SensorNodeState
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.remote.GatewayApiService
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.local.SensorRecordEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 网关 HTTP 数据仓库
 * 通过 HTTP 从网关拉取批量数据 + 状态信息
 */
@Singleton
class GatewayHttpRepository @Inject constructor(
    private val gatewayHeartbeatDao: GatewayHeartbeatDao,
    private val sensorRecordDao: SensorRecordDao
) {
    private var apiService: GatewayApiService? = null

    /** 网关状态流 */
    private val _gatewayStatus = MutableStateFlow<GatewayStatus?>(null)
    val gatewayStatus: StateFlow<GatewayStatus?> = _gatewayStatus.asStateFlow()

    /** 节点列表流 */
    private val _nodeStates = MutableStateFlow<List<SensorNodeState>>(emptyList())
    val nodeStates: StateFlow<List<SensorNodeState>> = _nodeStates.asStateFlow()

    /** 设置网关 API 基础 URL */
    fun setBaseUrl(baseUrl: String, service: GatewayApiService) {
        this.apiService = service
    }

    /** 获取网关状态 */
    suspend fun fetchGatewayStatus(): GatewayStatus? {
        val api = apiService ?: return null
        return try {
            val response = api.getGatewayStatus()
            if (response.isSuccessful) {
                val status = response.body()
                status?.let { _gatewayStatus.value = it }
                // 存心跳记录
                status?.let {
                    gatewayHeartbeatDao.insert(
                        GatewayHeartbeatEntity(
                            gwId = it.gwId,
                            unicastAddr = it.unicastAddr,
                            role = it.role.name,
                            isOnline = it.isOnline,
                            rssi = it.rssi,
                            relayCoverage = it.relayCoverage,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                status
            } else null
        } catch (e: Exception) {
            // 离线状态
            val offline = _gatewayStatus.value?.copy(isOnline = false) ?: GatewayStatus(
                gwId = "unknown",
                unicastAddr = 0,
                role = GatewayRole.UNKNOWN,
                isOnline = false,
                rssi = 0,
                lastHeartbeat = 0
            )
            _gatewayStatus.value = offline
            null
        }
    }

    /** 获取所有节点列表 */
    suspend fun fetchNodeList(): List<SensorNodeState> {
        val api = apiService ?: return emptyList()
        return try {
            val response = api.getNodeList()
            if (response.isSuccessful) {
                response.body()?.also { _nodeStates.value = it } ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 获取指定节点详情 */
    suspend fun fetchNodeDetail(addr: Int): SensorNodeState? {
        val api = apiService ?: return null
        return try {
            val response = api.getNodeDetail(addr)
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    /** 获取网关心跳历史 */
    fun observeGatewayHeartbeat(gwId: String) = gatewayHeartbeatDao.observeByGateway(gwId)

    /** 获取所有网关 ID */
    suspend fun getAllGatewayIds(): List<String> = gatewayHeartbeatDao.getAllGatewayIds()

    /** 获取所有网关最新状态 */
    suspend fun getLatestGateways(): List<GatewayHeartbeatEntity> =
        gatewayHeartbeatDao.getLatestPerGateway()
}
