package no.nordicsemi.android.nrfmesh.coldchain

/**
 * 医药冷链 BLE Mesh 密钥与地址常量（V5 — 网关组方案）
 * 
 * 地址分配（参照 gateway_group_analysis.md）:
 *   - 0x0001: 手机 App (Provisioner, 库默认)
 *   - 0x0002~0x000A: 网关节点 (最多 10 个)
 *   - 0x0011~0x00FF: 传感器节点 (LPN)
 * 
 * Group Address:
 *   - 0xC001: 传感器数据组 (所有网关订阅)
 *   - 0xC002: 告警数据组
 *   - 0xC003: 配置命令组
 */
object ColdChainKeys {
    /* ─── App Key ─── */
    @JvmField
    val APP_KEY_VAL: ByteArray = byteArrayOf(
        0x01.toByte(), 0x23.toByte(), 0x45.toByte(), 0x67.toByte(),
        0x89.toByte(), 0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte(),
        0xFE.toByte(), 0xDC.toByte(), 0xBA.toByte(), 0x98.toByte(),
        0x76.toByte(), 0x54.toByte(), 0x32.toByte(), 0x10.toByte()
    )

    const val APP_KEY_INDEX: Int = 0
    const val APP_KEY_NAME: String = "冷链AppKey (主)"

    /* ─── Net Key ─── */
    const val NET_KEY_INDEX: Int = 0
    const val NET_KEY_NAME: String = "冷链网络密钥"

    /* ─── 地址分配（V5 网关组方案） ─── */
    /** 网关起始地址 */
    const val GATEWAY_START_ADDR: Int = 0x0002
    /** 网关结束地址（最多 10 个） */
    const val GATEWAY_END_ADDR: Int = 0x000A
    /** 传感器起始地址 */
    const val SENSOR_START_ADDR: Int = 0x0011

    /** 判断是否为网关地址 */
    @JvmStatic
    fun isGatewayAddr(addr: Int): Boolean = addr in GATEWAY_START_ADDR..GATEWAY_END_ADDR
    /** 判断是否为传感器地址 */
    @JvmStatic
    fun isSensorAddr(addr: Int): Boolean = addr >= SENSOR_START_ADDR

    /** 单播地址范围 */
    const val UNICAST_RANGE_LOW: Int = 0x0001
    const val UNICAST_RANGE_HIGH: Int = 0x00FF

    /** 组播地址范围 */
    const val GROUP_RANGE_LOW: Int = 0xC000
    const val GROUP_RANGE_HIGH: Int = 0xCC9A

    /* ─── Group Address（网关组方案） ─── */
    const val GROUP_SENSOR_DATA: Int = 0xC001  // 传感器数据组
    const val GROUP_ALARM: Int = 0xC002        // 告警数据组
    const val GROUP_CONFIG: Int = 0xC003       // 配置命令组

    /* ─── Provisioner ─── */
    const val PROVISIONER_NAME: String = "手机 APP Provisioner"

    /* ─── 厂商模型 ─── */
    const val CID_ESP: Int = 0x02E5
    const val VENDOR_MODEL_ID: Int = (CID_ESP shl 16) or 0x0000  /* = 0x02E50000 */

    /* ─── OOB ─── */
    const val STATIC_OOB_TYPE: Int = 0

    /* ─── Mesh 网络 UUID ─── */
    const val NETWORK_MESH_UUID: String = "ColdChain_Mesh_Network_V3"

    /** NetKey 生成（固定种子） */
    fun generateNetKey(): ByteArray {
        val key = ByteArray(16)
        System.arraycopy(
            byteArrayOf(
                0x7D.toByte(), 0xD7.toByte(), 0x36.toByte(), 0x4C.toByte(),
                0xD8.toByte(), 0x42.toByte(), 0xAD.toByte(), 0x18.toByte(),
                0xC1.toByte(), 0x7C.toByte(), 0x2B.toByte(), 0x82.toByte(),
                0x0C.toByte(), 0x84.toByte(), 0xC3.toByte(), 0xD6.toByte()
            ), 0, key, 0, 16
        )
        return key
    }
}
