package no.nordicsemi.android.nrfmesh.coldchain.v5.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.local.AlarmEventDao
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.local.SensorRecordEntity
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.repository.DashboardRepository
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.repository.DashboardSnapshot
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.repository.GatewayHttpRepository
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.repository.MeshDataRepository
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val meshDataRepository: MeshDataRepository,
    private val gatewayHttpRepository: GatewayHttpRepository,
    private val alarmEventDao: AlarmEventDao
) : ViewModel() {

    /** 仪表盘聚合快照 */
    val snapshot: StateFlow<DashboardSnapshot> = dashboardRepository.dashboardSnapshot

    /** 未确认告警计数 */
    val unacknowledgedAlarmCount: StateFlow<Int> = alarmEventDao
        .observeUnacknowledgedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 最近传感器记录 */
    val recentRecords: StateFlow<List<SensorRecordEntity>> = meshDataRepository
        .observeRecentRecords(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 网关状态 */
    val gatewayStatus = gatewayHttpRepository.gatewayStatus

    /** 是否正在刷新 */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        // 初始化加载
        refresh()
    }

    /** 刷新仪表盘（Mesh + HTTP） */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                dashboardRepository.refreshDashboard()
                gatewayHttpRepository.fetchGatewayStatus()
                meshDataRepository.getLatestPerNode()
            } catch (_: Exception) {
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** 获取在线传感器数 */
    fun getOnlineCount(): Int = snapshot.value.onlineSensorCount

    /** 获取告警数 */
    fun getAlarmCount(): Int = unacknowledgedAlarmCount.value

    /** 获取低电量传感器数 */
    fun getLowBatteryCount(): Int = snapshot.value.lowBatteryCount
}
