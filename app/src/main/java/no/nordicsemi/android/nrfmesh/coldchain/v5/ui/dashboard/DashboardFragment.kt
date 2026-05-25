package no.nordicsemi.android.nrfmesh.coldchain.v5.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import no.nordicsemi.android.nrfmesh.R
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.local.SensorRecordEntity
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.repository.ProvisionedNodeInfo
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class DashboardFragment : Fragment(R.layout.fragment_v5_dashboard) {

    private val viewModel: DashboardViewModel by viewModels()

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var cardSensorCount: TextView
    private lateinit var cardAlarmCount: TextView
    private lateinit var cardGatewayStatus: TextView
    private lateinit var tvRecentData: TextView
    private lateinit var tvGatewayInfo: TextView
    private lateinit var tvNodeList: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        cardSensorCount = view.findViewById(R.id.cardSensorCount)
        cardAlarmCount = view.findViewById(R.id.cardAlarmCount)
        cardGatewayStatus = view.findViewById(R.id.cardGatewayStatus)
        tvRecentData = view.findViewById(R.id.tvRecentData)
        tvGatewayInfo = view.findViewById(R.id.tvGatewayInfo)
        tvNodeList = view.findViewById(R.id.tvNodeList)

        swipeRefresh.setOnRefreshListener { viewModel.refresh() }

        observeData()
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.snapshot.collectLatest { snapshot ->
                cardSensorCount.text = buildString {
                    append("设备\n")
                    append("${snapshot.onlineSensorCount} 在线")
                    if (snapshot.gwCount > 0) append(" | ${snapshot.gwCount}网关")
                    if (snapshot.sensorCount > 0) append(" | ${snapshot.sensorCount}传感器")
                    if (snapshot.lowBatteryCount > 0) append(" | ${snapshot.lowBatteryCount}低电")
                }
                // 显示已配网设备列表
                tvNodeList.text = buildNodeListText(snapshot.provisionedNodes)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.unacknowledgedAlarmCount.collectLatest { count ->
                cardAlarmCount.text = buildString {
                    append("告警\n")
                    if (count > 0) append("$count 个活跃告警") else append("无活跃告警")
                }
                if (count > 0) {
                    (cardAlarmCount.parent as? CardView)?.setCardBackgroundColor(0xFFD32F2F.toInt())
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.gatewayStatus.collectLatest { status ->
                val online = status?.isOnline == true
                cardGatewayStatus.text = buildString {
                    append("网关\n")
                    if (status != null) {
                        append(if (online) "✓ 在线" else "✗ 离线")
                        if (online) append(" RSSI=${status.rssi}")
                    } else {
                        append("HTTP未连")
                    }
                }
                tvGatewayInfo.text = if (status != null) {
                    "${if (status.isOnline) "在线" else "离线"} | ${status.role.name}"
                } else "网关 HTTP 未连接"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.recentRecords.collectLatest { records ->
                tvRecentData.text = buildSensorDataText(records)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isRefreshing.collectLatest { refreshing ->
                swipeRefresh.isRefreshing = refreshing
            }
        }
    }

    private fun buildNodeListText(nodes: List<ProvisionedNodeInfo>): String {
        if (nodes.isEmpty()) return "暂无已配网设备"
        return buildString {
            append("═══ 已配网设备 ═══\n")
            nodes.forEach { n ->
                val icon = when (n.role) { "网关" -> "🌐" else -> "📊" }
                val data = if (n.hasSensorData) {
                    " | ${"%.1f".format(n.lastTemp)}°C ${"%.1f".format(n.lastHumi)}%RH 电池${n.batteryPct}%"
                } else {
                    " | (等待采样数据)"
                }
                append("$icon ${n.name} [0x${Integer.toHexString(n.unicastAddr).uppercase()}]$data\n")
            }
        }
    }

    private fun buildSensorDataText(records: List<SensorRecordEntity>): String {
        if (records.isEmpty()) return "暂无传感器上传数据\n(需传感器周期性 Publish 到 Group 0xC001)"
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return buildString {
            append("═══ 最近采样 ═══\n")
            records.take(10).forEach { r ->
                val time = try { sdf.format(Date(r.receivedAt)) } catch (_: Exception) { "--:--:--" }
                append("${r.nodeName} | ${r.temp / 100f}°C / ${r.humi / 100f}%RH | 电池${r.batteryPct}% | $time\n")
            }
        }
    }
}
