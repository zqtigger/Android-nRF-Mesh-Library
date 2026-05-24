package no.nordicsemi.android.nrfmesh.coldchain.v5.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.local.AlarmEventDao
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.local.AlarmEventEntity
import javax.inject.Inject

@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val alarmEventDao: AlarmEventDao
) : ViewModel() {

    /** 当前 Tab: 0=未处理, 1=已确认, 2=全部历史 */
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    /** 所有告警事件 */
    val allAlarms: StateFlow<List<AlarmEventEntity>> = alarmEventDao
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 未确认告警 */
    val unacknowledgedAlarms: StateFlow<List<AlarmEventEntity>> = alarmEventDao
        .observeUnacknowledged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 未确认告警计数 */
    val unacknowledgedCount: StateFlow<Int> = alarmEventDao
        .observeUnacknowledgedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setTab(tab: Int) {
        _currentTab.value = tab
    }

    fun acknowledgeAlarm(eventId: Long) {
        viewModelScope.launch {
            alarmEventDao.acknowledge(eventId)
        }
    }

    fun acknowledgeAllForNode(nodeAddr: Int) {
        viewModelScope.launch {
            alarmEventDao.acknowledgeAllForNode(nodeAddr)
        }
    }
}
