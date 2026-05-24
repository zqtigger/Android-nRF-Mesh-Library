package no.nordicsemi.android.nrfmesh.coldchain.v5.ui.alarm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import no.nordicsemi.android.nrfmesh.R
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.local.AlarmEventEntity
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class AlarmCenterFragment : Fragment(R.layout.fragment_v5_alarm) {

    private val viewModel: AlarmViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvAlarms = view.findViewById<RecyclerView>(R.id.rvAlarms)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        val adapter = AlarmAdapter { event -> viewModel.acknowledgeAlarm(event.id) }

        rvAlarms.layoutManager = LinearLayoutManager(requireContext())
        rvAlarms.adapter = adapter

        // Tab 切换
        view.findViewById<TextView>(R.id.tabUnacknowledged).setOnClickListener {
            viewModel.setTab(0)
        }
        view.findViewById<TextView>(R.id.tabAcknowledged).setOnClickListener {
            viewModel.setTab(1)
        }
        view.findViewById<TextView>(R.id.tabAll).setOnClickListener {
            viewModel.setTab(2)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allAlarms.collectLatest { alarms ->
                val filtered = when (viewModel.currentTab.value) {
                    0 -> alarms.filter { !it.acknowledged }
                    1 -> alarms.filter { it.acknowledged }
                    else -> alarms
                }
                adapter.submitList(filtered)
                tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}

class AlarmAdapter(
    private val onAcknowledge: (AlarmEventEntity) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.ViewHolder>() {

    private var items = listOf<AlarmEventEntity>()

    fun submitList(list: List<AlarmEventEntity>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_v5_alarm, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onAcknowledge)
    }

    override fun getItemCount() = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvNode: TextView = itemView.findViewById(R.id.tvAlarmNode)
        private val tvInfo: TextView = itemView.findViewById(R.id.tvAlarmInfo)
        private val btnAck: TextView = itemView.findViewById(R.id.btnAcknowledge)

        fun bind(event: AlarmEventEntity, onAcknowledge: (AlarmEventEntity) -> Unit) {
            tvNode.text = event.nodeName
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            tvInfo.text = buildString {
                append("${event.alarmType}  ")
                append("${"%.1f".format(event.value)}°C  ")
                if (event.durationSeconds > 0) append("持续${event.durationSeconds / 60}分钟  ")
                append(sdf.format(Date(event.timestamp)))
            }
            btnAck.visibility = if (event.acknowledged) View.GONE else View.VISIBLE
            btnAck.setOnClickListener { onAcknowledge(event) }
        }
    }
}
