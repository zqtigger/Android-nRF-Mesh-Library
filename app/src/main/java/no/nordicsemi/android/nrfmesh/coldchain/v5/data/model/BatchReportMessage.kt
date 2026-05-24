package no.nordicsemi.android.nrfmesh.coldchain.v5.data.model

/**
 * 传感器批量上报消息 (OpCode 0xC2)
 * V5 核心消息类型：Sensor 批量上传采样数据
 */
data class BatchReportMessage(
    val batchId: Int,
    val count: Int,               // 本批记录数（1~20）
    val records: List<SensorRecordV2>
)
