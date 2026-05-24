package no.nordicsemi.android.nrfmesh.coldchain.v5.mesh

import no.nordicsemi.android.nrfmesh.coldchain.v5.data.model.AlarmReportMessage
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.model.BatchReportMessage
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.model.SensorRecordV2
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * V5 Mesh 消息解析器
 * 解析传感器自定义 Vendor OpCode 消息
 */
object MeshMessageParser {

    /**
     * 解析传感器数据上报 (OpCode 0xC1)
     * Payload 格式 (24 bytes):
     *   [0-3]   seq_num (uint32)
     *   [4-7]   timestamp (uint32, Unix秒)
     *   [8-9]   temp (int16, ×100)
     *   [10-11] humi (uint16, ×100)
     *   [12]    battery_pct (uint8)
     *   [13]    alarm_flag (uint8)
     *   [14-45] hash_prev (32 bytes, optional)
     */
    fun parseSensorDataReport(payload: ByteArray): SensorRecordV2? {
        if (payload.size < 14) return null
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val seqNum = buf.int
        val timestamp = buf.int.toLong() and 0xFFFFFFFFL
        val temp = buf.short
        val humi = buf.char.toInt()
        val batteryPct = buf.get().toInt() and 0xFF
        val alarmFlag = buf.get().toInt() and 0xFF
        val hashPrev = if (payload.size >= 46) payload.copyOfRange(14, 46) else null
        return SensorRecordV2(seqNum, timestamp, temp, humi, batteryPct, alarmFlag, hashPrev)
    }

    /**
     * 解析批量数据上报 (OpCode 0xC2)
     * Payload 格式:
     *   [0-1]   batch_id (uint16)
     *   [2]     count (uint8, 1~8)
     *   [3..]   逐条记录 (每条 14 或 46 字节)
     */
    fun parseBatchReport(payload: ByteArray): BatchReportMessage? {
        if (payload.size < 3) return null
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val batchId = buf.char.toInt()
        val count = (buf.get().toInt() and 0xFF).coerceAtMost(8)
        val records = mutableListOf<SensorRecordV2>()
        // 判断每条记录大小（14 = 无hash, 46 = 有32字节hash）
        val recordSize = if (payload.size - 3 >= count * 46) 46 else 14
        var offset = 3
        for (i in 0 until count) {
            if (offset + recordSize > payload.size) break
            val recordPayload = payload.copyOfRange(offset, offset + recordSize)
            parseSensorDataReport(recordPayload)?.let { records.add(it) }
            offset += recordSize
        }
        return BatchReportMessage(batchId, records.size, records)
    }

    /**
     * 解析报警数据上报 (OpCode 0xC3)
     * Payload 格式:
     *   [0-13]  SensorRecordV2 (14 bytes)
     *   [14-77] ECDSA 签名 (64 bytes)
     */
    fun parseAlarmReport(payload: ByteArray): AlarmReportMessage? {
        if (payload.size < 78) return null
        val recordPayload = payload.copyOfRange(0, 14)
        val signature = payload.copyOfRange(14, 78)
        val record = parseSensorDataReport(recordPayload) ?: return null
        return AlarmReportMessage(record, signature)
    }

    /**
     * 解析状态响应 (OpCode 0xC6)
     * Payload 格式 (6 bytes):
     *   [0-1]   battery_pct (uint16)
     *   [2-3]   cache_count (uint16, 待上传缓存条数)
     *   [4]     rssi (int8)
     *   [5]     flags (uint8)
     */
    fun parseStatusResponse(payload: ByteArray): StatusInfo? {
        if (payload.size < 6) return null
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val batteryPct = buf.char.toInt()
        val cacheCount = buf.char.toInt()
        val rssi = buf.get().toInt()
        val flags = buf.get().toInt() and 0xFF
        return StatusInfo(batteryPct, cacheCount, rssi, flags)
    }

    data class StatusInfo(
        val batteryPct: Int,
        val cacheCount: Int,
        val rssi: Int,
        val flags: Int
    ) {
        val isAlarmActive: Boolean get() = (flags and 0x01) != 0
        val isLowBattery: Boolean get() = (flags and 0x02) != 0
    }
}
