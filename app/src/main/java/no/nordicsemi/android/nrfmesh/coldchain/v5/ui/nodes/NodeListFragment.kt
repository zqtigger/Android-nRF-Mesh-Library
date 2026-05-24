package no.nordicsemi.android.nrfmesh.coldchain.v5.ui.nodes

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import no.nordicsemi.android.nrfmesh.R
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.model.NodeRole
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.model.SensorNodeState

@AndroidEntryPoint
class NodeListFragment : Fragment(R.layout.fragment_v5_node_list) {

    private val viewModel: NodeListViewModel by viewModels()

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NodeListAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var spinnerRole: Spinner
    private lateinit var spinnerStatus: Spinner
    private lateinit var etSearch: EditText

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.rvNodeList)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        spinnerRole = view.findViewById(R.id.spinnerRole)
        spinnerStatus = view.findViewById(R.id.spinnerStatus)
        etSearch = view.findViewById(R.id.etSearch)

        adapter = NodeListAdapter { node -> onNodeClicked(node) }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        setupFilters()
        swipeRefresh.setOnRefreshListener { viewModel.refresh() }

        observeData()
    }

    private fun setupFilters() {
        // 角色筛选
        val roles = listOf("全部") + NodeRole.entries.map { it.label }
        spinnerRole.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_dropdown_item, roles)
        spinnerRole.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                viewModel.setFilterRole(
                    if (pos == 0) null else NodeRole.entries.getOrNull(pos - 1)
                )
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 在线状态筛选
        val statuses = listOf("全部", "在线", "离线")
        spinnerStatus.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_dropdown_item, statuses)
        spinnerStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                viewModel.setFilterOnline(
                    when (pos) { 1 -> true; 2 -> false; else -> null }
                )
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 搜索
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.setSearchQuery(s?.toString() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.filteredNodes.collectLatest { nodes ->
                adapter.submitList(nodes)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isRefreshing.collectLatest { refreshing ->
                swipeRefresh.isRefreshing = refreshing
            }
        }
    }

    private fun onNodeClicked(node: SensorNodeState) {
        // 导航到节点详情（占位实现）
        // findNavController().navigate(...)
    }
}
