package no.nordicsemi.android.nrfmesh.coldchain.v5.mesh

import android.util.Log
import no.nordicsemi.android.mesh.transport.MeshMessage
import no.nordicsemi.android.mesh.transport.VendorModelMessageUnacked
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.repository.MeshDataRepository

/**
 * V5 ColdChain Sensor Vendor Model 消息处理器
 * 
 * 处理来自 Sensor 的 Vendor OpCode 消息：
 * - 0xC1: 单条数据上报
 * - 0xC2: 批量数据上报（核心）
 * - 0xC3: 报警数据上报
 * - 0xC6: 状态响应
 * 
 * 注意：nRF Mesh Library 中 Vendor Model 通过 VendorModel/VendorModelMessage*
 * 类体系处理。此 handler 在 Mesh 层收到 Vendor Message 时被调用。
 */
object ColdChainSensorModelHandler {
    private const val TAG = "ColdChainVendor"

    /**
     * 处理收到的 Vendor Model 消息
     * 
     * @param src 源节点 Unicast 地址
     * @param opCode Vendor OpCode (0xC1~0xC6)
     * @param payload 消息载荷
     * @param nodeName 节点名称
     * @param repository 数据仓库
     */
    suspend fun handleVendorMessage(
        src: Int,
        opCode: Int,
        payload: ByteArray,
        nodeName: String,
        repository: MeshDataRepository
    ) {
        Log.d(TAG, "Received vendor msg: src=0x${Integer.toHexString(src)}, opCode=0x${Integer.toHexString(opCode)}, len=${payload.size}")

        when (opCode) {
            VendorOpCodes.SENSOR_DATA_REPORT -> {
                Log.i(TAG, "→ SENSOR_DATA_REPORT (0xC1)")
                repository.handleSensorDataReport(src, nodeName, payload)
            }
            VendorOpCodes.SENSOR_BATCH_REPORT -> {
                Log.i(TAG, "→ SENSOR_BATCH_REPORT (0xC2) [核心批量数据]")
                repository.handleBatchReport(src, nodeName, payload)
            }
            VendorOpCodes.SENSOR_ALARM_REPORT -> {
                Log.i(TAG, "→ SENSOR_ALARM_REPORT (0xC3) [报警数据]")
                repository.handleAlarmReport(src, nodeName, payload)
            }
            VendorOpCodes.STATUS_RESPONSE -> {
                Log.d(TAG, "→ STATUS_RESPONSE (0xC6)")
                repository.handleStatusResponse(src, payload)
            }
            else -> {
                Log.w(TAG, "Unknown vendor opCode: 0x${Integer.toHexString(opCode)}")
            }
        }
    }

    /**
     * 创建时间同步消息
     */
    fun buildTimeSyncMessage(timestampSeconds: Long): ByteArray {
        // 8 字节小端 Unix 时间戳
        val buf = java.nio.ByteBuffer.allocate(8)
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buf.putLong(timestampSeconds)
        return buf.array()
    }
}
