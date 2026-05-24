package no.nordicsemi.android.nrfmesh.coldchain

/**
 * 医药冷链 BLE Mesh 密钥与地址常量（V3）
 * 
 * 与网关固件 main/ble_mesh_friend.c 中的配置完全一致：
 * - AppKey: 相同的 16 字节硬编码密钥
 * - 网关节点地址: 0x0001
 * - 传感器起始地址: 0x0002
 * - 公司ID: ESP 0x02E5
 * - Vendor Model ID: 0x0000（传感器 Sensor Status 模型）
 */
object ColdChainKeys {
    /* ─── App Key ─── */
    /** 应用密钥（与网关固件 ble_mesh_friend.c 硬编码一致）  */
    @JvmField
    val APP_KEY_VAL: ByteArray = byteArrayOf(
        0x01.toByte(), 0x23.toByte(), 0x45.toByte(), 0x67.toByte(),
        0x89.toByte(), 0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte(),
        0xFE.toByte(), 0xDC.toByte(), 0xBA.toByte(), 0x98.toByte(),
        0x76.toByte(), 0x54.toByte(), 0x32.toByte(), 0x10.toByte()
    )

    /** AppKey 索引（主密钥）  */
    const val APP_KEY_INDEX: Int = 0

    /** AppKey 名称  */
    const val APP_KEY_NAME: String = "冷链AppKey (主)"

    /* ─── Net Key ─── */
    /** NetKey 索引  */
    const val NET_KEY_INDEX: Int = 0

    /** NetKey 名称  */
    const val NET_KEY_NAME: String = "冷链网络密钥"

    /* ─── 地址分配 ─── */
    /** 网关节点地址（Friend + Proxy 角色）  */
    const val GATEWAY_UNICAST_ADDR: Int = 0x0001

    /** 传感器节点起始地址  */
    const val SENSOR_START_ADDR: Int = 0x0002

    /** 单播地址范围（为 50 个节点预留）  */
    const val UNICAST_RANGE_LOW: Int = 0x0001
    const val UNICAST_RANGE_HIGH: Int = 0x0033 /* 0x0001 ~ 0x0033 = 51 addresses */

    /** 组播地址范围  */
    const val GROUP_RANGE_LOW: Int = 0xC000
    const val GROUP_RANGE_HIGH: Int = 0xCC9A

    /* ─── Provisioner 信息 ─── */
    const val PROVISIONER_NAME: String = "手机 APP Provisioner"

    /* ─── 厂商模型 ─── */
    /** 乐鑫 CID  */
    const val CID_ESP: Int = 0x02E5

    /** Vendor Model ID = (CID << 16) | 0x0000 = 0x02E50000
     *  ⭐ 必须是 32-bit 格式（>0x7FFF）才能被 nRF Mesh 库识别为 Vendor Model */
    const val VENDOR_MODEL_ID: Int = (CID_ESP shl 16) or 0x0000  /* = 0x02E50000 */

    /* ─── OOB ─── */
    /** 使用 No OOB 认证  */
    const val STATIC_OOB_TYPE: Int = 0

    /* ─── Mesh 网络 UUID ─── */
    const val NETWORK_MESH_UUID: String = "ColdChain_Mesh_Network_V3"

    /* ─── 工具：生成 NetKey ─── */
    /**
     * 使用固定种子生成确定性 NetKey（确保每次网络重建时一致）
     * @return 16字节 NetKey
     */
    fun generateNetKey(): ByteArray {
        // 使用项目固定种子生成 NetKey
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
