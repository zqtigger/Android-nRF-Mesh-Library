package no.nordicsemi.android.nrfmesh.coldchain.v5.ui.nodes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.local.SensorRecordEntity
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.model.NodeRole
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.model.SensorNodeState
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.repository.GatewayHttpRepository
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.repository.MeshDataRepository
import javax.inject.Inject

@HiltViewModel
class NodeListViewModel @Inject constructor(
    private val meshDataRepository: MeshDataRepository,
    private val gatewayHttpRepository: GatewayHttpRepository
) : ViewModel() {

    /** 所有节点状态 */
    private val _nodeStates = MutableStateFlow<List<SensorNodeState>>(emptyList())
    val nodeStates: StateFlow<List<SensorNodeState>> = _nodeStates.asStateFlow()

    /** 筛选条件 */
    private val _filterRole = MutableStateFlow<NodeRole?>(null)
    val filterRole: StateFlow<NodeRole?> = _filterRole.asStateFlow()

    private val _filterOnline = MutableStateFlow<Boolean?>(null)
    val filterOnline: StateFlow<Boolean?> = _filterOnline.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** 过滤后的节点列表 */
    val filteredNodes: StateFlow<List<SensorNodeState>> = combine(
        _nodeStates, _filterRole, _filterOnline, _searchQuery
    ) { nodes, role, online, query ->
        nodes.filter { node ->
            (role == null || node.nodeRole == role) &&
                    (online == null || node.isOnline == online) &&
                    (query.isEmpty() || node.nodeName.contains(query, ignoreCase = true) ||
                            query in Integer.toHexString(node.unicastAddr).uppercase())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 是否正在刷新 */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadNodes()
    }

    fun loadNodes() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // 从 HTTP 拉取节点列表
                val httpNodes = gatewayHttpRepository.fetchNodeList()
                if (httpNodes.isNotEmpty()) {
                    _nodeStates.value = httpNodes
                } else {
                    // 回退：从本地缓存构建节点状态
                    buildNodesFromCache()
                }
            } catch (_: Exception) {
                buildNodesFromCache()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun buildNodesFromCache() {
        val latestRecords = meshDataRepository.getLatestPerNode()
        _nodeStates.value = latestRecords.map { record ->
            SensorNodeState(
                unicastAddr = record.nodeAddr,
                nodeName = record.nodeName,
                uuid = "",
                temperatureC = record.temp / 100f,
                humidityRH = record.humi / 100f,
                batteryPct = record.batteryPct,
                rssi = record.rssi,
                lastSampleTime = record.receivedAt,
                cacheCount = 0,
                isOnline = true,
                alarmActive = record.alarmFlag != 0
            )
        }
    }

    fun setFilterRole(role: NodeRole?) {
        _filterRole.value = role
    }

    fun setFilterOnline(online: Boolean?) {
        _filterOnline.value = online
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refresh() {
        loadNodes()
    }
}
