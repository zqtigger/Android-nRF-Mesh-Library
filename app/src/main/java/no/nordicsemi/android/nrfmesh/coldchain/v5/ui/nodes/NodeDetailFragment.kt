package no.nordicsemi.android.nrfmesh.coldchain.v5.ui.nodes

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import no.nordicsemi.android.nrfmesh.R
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.model.SensorNodeState
import java.text.SimpleDateFormat
import java.util.*

/**
 * 节点详情 Fragment（占位实现 - Phase 2 完善图表）
 * 
 * 显示：基本信息 + 温度/湿度 24h 折线图 + 操作区
 */
@AndroidEntryPoint
class NodeDetailFragment : Fragment(R.layout.fragment_v5_node_detail) {

    companion object {
        const val ARG_NODE_ADDR = "node_addr"

        fun newInstance(nodeAddr: Int): NodeDetailFragment {
            return NodeDetailFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_NODE_ADDR, nodeAddr)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val nodeAddr = arguments?.getInt(ARG_NODE_ADDR) ?: 0

        val tvDetail = view.findViewById<TextView>(R.id.tvNodeDetail)
        tvDetail.text = buildString {
            append("═══ 节点详情 ═══\n")
            append("地址: 0x${Integer.toHexString(nodeAddr).uppercase()}\n\n")
            append("24h 温度/湿度曲线将在 Phase 2 完善\n")
            append("(需要集成 MPAndroidChart 图表库)\n\n")
            append("操作区:\n")
            append("  ● 配置阈值\n")
            append("  ● 触发 OTA\n")
            append("  ● 手动校时\n")
            append("  ● 查看审计链\n")
            append("  ● 强制上传缓存\n")
        }
    }
}
