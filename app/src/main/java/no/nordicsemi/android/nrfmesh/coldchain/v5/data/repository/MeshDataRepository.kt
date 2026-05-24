package no.nordicsemi.android.nrfmesh.coldchain.v5.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.local.AlarmEventDao
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.local.AlarmEventEntity
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.local.SensorRecordDao
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.local.SensorRecordEntity
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.model.*
import no.nordicsemi.android.nrfmesh.coldchain.v5.mesh.MeshMessageParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mesh 数据仓库
 * 负责接收 Mesh Publish 消息、解析、存入 Room
 */
@Singleton
class MeshDataRepository @Inject constructor(
    private val sensorRecordDao: SensorRecordDao,
    private val alarmEventDao: AlarmEventDao
) {
    /** 最新接收的传感器记录（实时流，供仪表盘订阅） */
    private val _latestRecords = MutableStateFlow<List<SensorRecordEntity>>(emptyList())
    val latestRecords: StateFlow<List<SensorRecordEntity>> = _latestRecords.asStateFlow()

    /** 未确认告警计数 */
    val unacknowledgedAlarmCount: Flow<Int> = alarmEventDao.observeUnacknowledgedCount()

    /** 获取最近 N 条记录 */
    fun observeRecentRecords(limit: Int = 20): Flow<List<SensorRecordEntity>> {
        return sensorRecordDao.observeRecent(limit)
    }

    /** 获取所有记录计数 */
    fun observeRecordCount(): Flow<Int> {
        return sensorRecordDao.observeCount()
    }

    /** 获取各节点最新记录（仪表盘用） */
    suspend fun getLatestPerNode(): List<SensorRecordEntity> {
        return sensorRecordDao.getLatestPerNode()
    }

    /** 处理 Vendor Message (0xC1 单条上报) */
    suspend fun handleSensorDataReport(nodeAddr: Int, nodeName: String, payload: ByteArray) {
        val record = MeshMessageParser.parseSensorDataReport(payload) ?: return
        val entity = record.toEntity(nodeAddr, nodeName, "mesh")
        sensorRecordDao.insert(entity)
        updateLatestRecords()
    }

    /** 处理 Vendor Message (0xC2 批量上报) */
    suspend fun handleBatchReport(nodeAddr: Int, nodeName: String, payload: ByteArray) {
        val batch = MeshMessageParser.parseBatchReport(payload) ?: return
        val entities = batch.records.map { it.toEntity(nodeAddr, nodeName, "mesh") }
        sensorRecordDao.insertAll(entities)
        updateLatestRecords()
    }

    /** 处理 Vendor Message (0xC3 报警上报) */
    suspend fun handleAlarmReport(nodeAddr: Int, nodeName: String, payload: ByteArray) {
        val alarm = MeshMessageParser.parseAlarmReport(payload) ?: return
        // 存传感器记录
        val entity = alarm.record.toEntity(nodeAddr, nodeName, "mesh")
        sensorRecordDao.insert(entity)

        // 创建告警事件
        val alarmType = when {
            alarm.record.temperatureC > 8.0f -> AlarmType.OVER_TEMP
            alarm.record.temperatureC < 2.0f -> AlarmType.UNDER_TEMP
            alarm.record.humidityRH > 65f -> AlarmType.OVER_HUMIDITY
            alarm.record.batteryPct < 10 -> AlarmType.LOW_BATTERY
            else -> AlarmType.SENSOR_FAULT
        }
        val alarmEvent = AlarmEventEntity(
            nodeAddr = nodeAddr,
            nodeName = nodeName,
            alarmType = alarmType.name,
            value = alarm.record.temperatureC,
            threshold = if (alarmType == AlarmType.OVER_TEMP) 8.0f else 2.0f,
            timestamp = System.currentTimeMillis(),
            ecdsaSignature = alarm.ecdsaSignature
        )
        alarmEventDao.insert(alarmEvent)
        updateLatestRecords()
    }

    /** 处理 Vendor Message (0xC6 状态响应) */
    suspend fun handleStatusResponse(nodeAddr: Int, payload: ByteArray) {
        // 状态响应更新节点内存状态，这里仅记录
        MeshMessageParser.parseStatusResponse(payload)
    }

    /** 批量插入 HTTP 拉取的数据 */
    suspend fun insertHttpRecords(records: List<SensorRecordEntity>) {
        sensorRecordDao.insertAll(records)
        updateLatestRecords()
    }

    /** 清理 7 天前数据 */
    suspend fun cleanOldData() {
        val sevenDaysAgo = System.currentTimeMillis() / 1000 - 7 * 24 * 3600
        sensorRecordDao.deleteOlderThan(sevenDaysAgo)
        alarmEventDao.deleteOlderThan(System.currentTimeMillis() - 30L * 24 * 3600 * 1000)
    }

    /** 在线传感器数 */
    suspend fun getOnlineSensorCount(): Int {
        return sensorRecordDao.getAllNodeAddrs().size
    }

    private suspend fun updateLatestRecords() {
        _latestRecords.value = sensorRecordDao.observeRecent(20).let { emptyList() }
        // 使用 getLatestPerNode 获取实时列表
        val latest = getLatestPerNode()
        _latestRecords.value = latest
    }
}

/**
 * 扩展函数：将 SensorRecordV2 转为 Room Entity
 */
fun SensorRecordV2.toEntity(nodeAddr: Int, nodeName: String, sourceType: String) = SensorRecordEntity(
    nodeAddr = nodeAddr,
    nodeName = nodeName,
    seqNum = seqNum,
    timestamp = timestamp,
    receivedAt = System.currentTimeMillis(),
    temp = temp.toInt(),
    humi = humi,
    batteryPct = batteryPct,
    alarmFlag = alarmFlag,
    hashPrev = hashPrev,
    sourceType = sourceType
)
