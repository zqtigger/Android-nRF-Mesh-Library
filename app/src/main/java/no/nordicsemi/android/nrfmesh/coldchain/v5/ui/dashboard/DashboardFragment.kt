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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        cardSensorCount = view.findViewById(R.id.cardSensorCount)
        cardAlarmCount = view.findViewById(R.id.cardAlarmCount)
        cardGatewayStatus = view.findViewById(R.id.cardGatewayStatus)
        tvRecentData = view.findViewById(R.id.tvRecentData)
        tvGatewayInfo = view.findViewById(R.id.tvGatewayInfo)

        swipeRefresh.setOnRefreshListener { viewModel.refresh() }

        observeData()
    }

    private fun observeData() {
        // 仪表盘快照
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.snapshot.collectLatest { snapshot ->
                cardSensorCount.text = buildString {
                    append("传感器\n")
                    append("${snapshot.onlineSensorCount} 在线")
                    if (snapshot.lowBatteryCount > 0) {
                        append(" | ${snapshot.lowBatteryCount} 低电")
                    }
                    append("\n平均电池 ${"%.1f".format(snapshot.avgBatteryPct)}%")
                }
            }
        }

        // 告警计数
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.unacknowledgedAlarmCount.collectLatest { count ->
                cardAlarmCount.text = buildString {
                    append("告警\n")
                    if (count > 0) append("$count 个活跃告警") else append("无活跃告警")
                }
                if (count > 0) {
                    (cardAlarmCount.parent as? CardView)?.setCardBackgroundColor(
                        0xFFD32F2F.toInt()
                    )
                }
            }
        }

        // 网关状态
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.gatewayStatus.collectLatest { status ->
                val online = status?.isOnline == true
                cardGatewayStatus.text = buildString {
                    append("网关\n")
                    if (status != null) {
                        append(if (online) "✓ 在线" else "✗ 离线")
                        if (online) append(" RSSI=${status.rssi}")
                        append("\n角色: ${status.role.name}")
                    } else {
                        append("未连接")
                    }
                }
                tvGatewayInfo.text = if (status != null) {
                    "Primary: ${if (status.isOnline) "在线 RSSI=${status.rssi}" else "离线"} | " +
                            "Relay覆盖率: ${"%.0f".format(status.relayCoverage * 100)}%"
                } else "网关未连接"
            }
        }

        // 最近记录
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.recentRecords.collectLatest { records ->
                tvRecentData.text = buildRecentDataText(records)
            }
        }

        // 刷新状态
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isRefreshing.collectLatest { refreshing ->
                swipeRefresh.isRefreshing = refreshing
            }
        }
    }

    private fun buildRecentDataText(records: List<SensorRecordEntity>): String {
        if (records.isEmpty()) return "暂无数据"
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return buildString {
            append("═══ 最近采样 ═══\n")
            records.take(10).forEach { r ->
                val time = try { sdf.format(Date(r.receivedAt)) } catch (_: Exception) { "--:--:--" }
                append("${r.nodeName} | ${r.temp / 100f}°C / ${r.humi / 100f}%RH | ")
                append("电池${r.batteryPct}% | $time\n")
            }
        }
    }
}
