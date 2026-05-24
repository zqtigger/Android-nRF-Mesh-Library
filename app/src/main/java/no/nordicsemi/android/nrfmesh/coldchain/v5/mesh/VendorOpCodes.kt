package no.nordicsemi.android.nrfmesh.coldchain.v5.mesh

/**
 * V5 ColdChain Sensor Vendor Model OpCode 定义
 * 
 * Company ID: 0x02E5 (Espressif)
 * Model ID: 0x0001 (ColdChain Sensor Model V5)
 */
object VendorOpCodes {
    /** 轻量单条普通数据上报 (Sensor → Mesh) */
    const val SENSOR_DATA_REPORT = 0xC1

    /** 批量数据上报 - 核心 (Sensor → Mesh) */
    const val SENSOR_BATCH_REPORT = 0xC2

    /** 报警数据上报 - 带 ECDSA 签名 (Sensor → Mesh) */
    const val SENSOR_ALARM_REPORT = 0xC3

    /** 配置下发 (Mesh → Sensor) */
    const val CONFIG_SET = 0xC4

    /** 时间权威广播/校时 (Mesh → Sensor) */
    const val TIME_SYNC = 0xC5

    /** 状态响应/心跳/缓存状态 (Sensor → Mesh) */
    const val STATUS_RESPONSE = 0xC6

    /** Vendor ID (Espressif) */
    const val VENDOR_ID = 0x02E5

    /** ColdChain Sensor Model ID */
    const val MODEL_ID = 0x0001

    /** 32-bit Vendor Model ID */
    const val VENDOR_MODEL_ID_32 = (VENDOR_ID shl 16) or MODEL_ID  // = 0x02E50001

    /** 传感器数据 Group 地址 */
    const val GROUP_SENSOR_DATA = 0xC001

    /** 传感器批量上报 Group 地址 */
    const val GROUP_BATCH_REPORT = 0xC002

    /** 告警 Group 地址 */
    const val GROUP_ALARM = 0xC005
}
