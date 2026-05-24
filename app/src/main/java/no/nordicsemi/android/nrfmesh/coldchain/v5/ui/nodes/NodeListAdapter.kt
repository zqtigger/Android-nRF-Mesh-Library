package no.nordicsemi.android.nrfmesh.coldchain.v5.ui.nodes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import no.nordicsemi.android.nrfmesh.R
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.model.NodeRole
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.model.SensorNodeState

class NodeListAdapter(
    private val onItemClick: (SensorNodeState) -> Unit
) : ListAdapter<SensorNodeState, NodeListAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_v5_node, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val node = getItem(position)
        holder.bind(node)
        holder.itemView.setOnClickListener { onItemClick(node) }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvNodeName)
        private val tvData: TextView = itemView.findViewById(R.id.tvNodeData)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvNodeStatus)

        fun bind(node: SensorNodeState) {
            tvName.text = buildString {
                append(roleIcon(node.nodeRole))
                append(" ")
                append(node.nodeName)
            }
            tvData.text = buildString {
                if (node.temperatureC != null) {
                    append("${"%.1f".format(node.temperatureC)}°C  ")
                }
                if (node.humidityRH != null) {
                    append("${"%.1f".format(node.humidityRH)}%RH  ")
                }
                append("电池${node.batteryPct}%  ")
                append("RSSI=${node.rssi}")
                if (node.cacheCount > 0) append("  缓存:${node.cacheCount}条")
            }
            tvStatus.text = when {
                !node.isOnline -> "离线"
                node.alarmActive -> "⚠ 报警"
                node.batteryPct < 20 -> "低电"
                else -> "正常"
            }
            tvStatus.setTextColor(when {
                !node.isOnline -> 0xFF9E9E9E.toInt()
                node.alarmActive -> 0xFFD32F2F.toInt()
                node.batteryPct < 20 -> 0xFFF57C00.toInt()
                else -> 0xFF4CAF50.toInt()
            })
        }

        private fun roleIcon(role: NodeRole): String = when (role) {
            NodeRole.GATEWAY -> "🌐"
            NodeRole.RELAY -> "📡"
            NodeRole.FRIEND -> "🤝"
            NodeRole.SENSOR -> "📊"
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SensorNodeState>() {
        override fun areItemsTheSame(old: SensorNodeState, new: SensorNodeState) =
            old.unicastAddr == new.unicastAddr

        override fun areContentsTheSame(old: SensorNodeState, new: SensorNodeState) =
            old == new
    }
}
