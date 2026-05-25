package no.nordicsemi.android.nrfmesh.coldchain.v5.ui.provision

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nordicsemi.android.mesh.*
import no.nordicsemi.android.mesh.provisionerstates.ProvisioningState
import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode
import no.nordicsemi.android.nrfmesh.R
import no.nordicsemi.android.nrfmesh.ble.BleMeshManager
import no.nordicsemi.android.nrfmesh.ble.BleMeshManagerCallbacks
import no.nordicsemi.android.nrfmesh.coldchain.ColdChainKeys
import no.nordicsemi.android.nrfmesh.coldchain.ColdChainNodeConfigurator
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.repository.MeshDataRepository
import no.nordicsemi.android.nrfmesh.coldchain.v5.mesh.ColdChainSensorModelHandler
import no.nordicsemi.android.mesh.transport.VendorModelMessageStatus
import no.nordicsemi.android.support.v18.scanner.*
import java.nio.ByteBuffer
import java.util.*
import javax.inject.Inject

/**
 * V5 配网 Fragment
 * 在 V5 主界面内完成 Mesh 网络创建 + BLE 扫描 + 设备配网，无需跳转独立 Activity
 */
@AndroidEntryPoint
class ProvisionFragment : Fragment(R.layout.fragment_v5_provision) {

    @Inject lateinit var meshManagerApi: MeshManagerApi
    @Inject lateinit var bleMeshManager: BleMeshManager
    @Inject lateinit var meshDataRepository: MeshDataRepository

    // UI
    private lateinit var btnCreateNetwork: Button
    private lateinit var btnResetNetwork: Button
    private lateinit var btnScan: Button
    private lateinit var tvNetworkInfo: TextView
    private lateinit var tvDeviceList: TextView
    private lateinit var tvProvisionedNodes: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var progressBar: ProgressBar

    // State
    private var meshNetwork: MeshNetwork? = null
    private var networkReady = false
    /** 网关下一个可用地址 (0x0002~0x000A)，从 ColdChainKeys.GATEWAY_START_ADDR 起 */
    private var nextGatewayAddr = ColdChainKeys.GATEWAY_START_ADDR
    /** 传感器下一个可用地址 (0x0011~0x00FF)，从 ColdChainKeys.SENSOR_START_ADDR 起 */
    private var nextSensorAddr = ColdChainKeys.SENSOR_START_ADDR
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false
    private var currentScanCallback: ScanCallback? = null
    private val foundDevices = mutableListOf<ScanResultWrapper>()
    private val configurator = ColdChainNodeConfigurator()

    // Provisioning state
    private var pendingDevice: ScanResultWrapper? = null
    private var pendingUnicastAddr = 0
    private var isConnecting = false
    private var capabilitiesReceived = false
    private var provisioningStarted = false
    private var isReconnectingForConfig = false
    private var pendingConfigNode: ProvisionedMeshNode? = null
    private var meshManagerCallbacks: MeshManagerCallbacks? = null

    // BLE permissions
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) log("BLE权限已授予") else log("BLE权限被拒绝")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnCreateNetwork = view.findViewById(R.id.btnCreateNetwork)
        btnResetNetwork = view.findViewById(R.id.btnResetNetwork)
        btnScan = view.findViewById(R.id.btnScan)
        tvNetworkInfo = view.findViewById(R.id.tvNetworkInfo)
        tvDeviceList = view.findViewById(R.id.tvDeviceList)
        tvProvisionedNodes = view.findViewById(R.id.tvProvisionedNodes)
        tvStatus = view.findViewById(R.id.tvStatus)
        tvLog = view.findViewById(R.id.tvLog)
        progressBar = view.findViewById(R.id.progressBar)

        bleMeshManager.setGattCallbacks(bleCallbacks)
        checkBlePermissions()

        btnCreateNetwork.setOnClickListener { createNetwork() }
        btnResetNetwork.setOnClickListener { askResetNetwork() }
        btnScan.setOnClickListener { startBleScan() }

        tvDeviceList.setOnClickListener {
            if (foundDevices.isEmpty()) return@setOnClickListener
            val items = foundDevices.mapIndexed { i, w ->
                "${w.device.name ?: "未配网设备"} [${w.device.address}] RSSI=${w.rssi}"
            }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("选择要配网的设备")
                .setItems(items) { _, which -> onDeviceClicked(which) }
                .show()
        }

        // 检查 Mesh Library 是否已从 Room DB 加载了持久化网络
        checkExistingNetwork()
        log("配网模块就绪，请点击「创建网络」开始")
        updateUi()
    }

    // ─── Permissions ───

    private fun checkBlePermissions() {
        val needs = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) needs.add(Manifest.permission.BLUETOOTH_SCAN)
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) needs.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) needs.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (needs.isNotEmpty()) permissionLauncher.launch(needs.toTypedArray())
    }

    // ─── Network Management ───
    // 注：nRF Mesh Library 强制 Provisioner 地址必须在 0x0001~0x7FFF
    // generateMeshNetwork() 默认分配 0x0001，我们保持不变
    // 配网节点地址从 0x0001 起依次分配（网关第一个，传感器随后）

    /** 检查 Mesh Library 是否已从 Room DB 加载了持久化网络 */
    private fun checkExistingNetwork() {
        try {
            val savedNetwork = meshManagerApi.meshNetwork
            if (savedNetwork != null && savedNetwork.meshUUID.isNotEmpty()
                && savedNetwork.nodes.isNotEmpty()) {
                meshNetwork = savedNetwork
                networkReady = true
                val nodes = savedNetwork.nodes
                val maxGw = nodes.filter { ColdChainKeys.isGatewayAddr(it.unicastAddress) }
                    .maxOfOrNull { it.unicastAddress }
                val maxSn = nodes.filter { ColdChainKeys.isSensorAddr(it.unicastAddress) }
                    .maxOfOrNull { it.unicastAddress }
                nextGatewayAddr = (maxGw?.plus(1) ?: ColdChainKeys.GATEWAY_START_ADDR)
                    .coerceIn(ColdChainKeys.GATEWAY_START_ADDR, ColdChainKeys.GATEWAY_END_ADDR + 1)
                nextSensorAddr = (maxSn?.plus(1) ?: ColdChainKeys.SENSOR_START_ADDR)
                    .coerceAtLeast(ColdChainKeys.SENSOR_START_ADDR)
                log("已加载现有网络: ${nodes.size}个节点 (网关${maxGw?.let{nodes.count{ColdChainKeys.isGatewayAddr(it.unicastAddress)}}?:0}, 传感器${maxSn?.let{nodes.count{ColdChainKeys.isSensorAddr(it.unicastAddress)}}?:0})")
            }
        } catch (_: Exception) { }
    }

    private fun createNetwork() {
        try {
            setupMeshCallbacks()
            meshManagerApi.createMeshNetwork()
            meshNetwork = meshManagerApi.meshNetwork
            if (meshNetwork != null) {
                handler.postDelayed({
                    try {
                        if (meshNetwork!!.getAppKey(ColdChainKeys.APP_KEY_INDEX) == null) {
                            val appKey = ApplicationKey(ColdChainKeys.APP_KEY_INDEX, ColdChainKeys.APP_KEY_VAL)
                            appKey.name = ColdChainKeys.APP_KEY_NAME
                            appKey.boundNetKeyIndex = ColdChainKeys.NET_KEY_INDEX
                            meshNetwork!!.addAppKey(appKey)
                            log("AppKey 已添加: 0123456789ABCDEF...")
                        }
                    } catch (e: Exception) { log("添加AppKey失败: ${e.message}") }
                    updateUi()
                }, 500)
                networkReady = true
                nextGatewayAddr = ColdChainKeys.GATEWAY_START_ADDR
                nextSensorAddr = ColdChainKeys.SENSOR_START_ADDR
                log("网络创建成功! Provisioner=0x0001, 网关起始=0x0002, 传感器起始=0x0011")
                toast("Mesh 网络已创建")
            }
        } catch (e: Exception) {
            log("创建网络失败: ${e.message}")
            toast("创建网络失败")
        }
        updateUi()
    }

    private fun askResetNetwork() {
        AlertDialog.Builder(requireContext())
            .setTitle("确认重置")
            .setMessage("确定要重置整个 Mesh 网络吗？所有已配网设备将被清除。")
            .setPositiveButton("确定") { _, _ -> resetNetwork() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun resetNetwork() {
        try {
            meshManagerApi.resetMeshNetwork()
            meshNetwork = meshManagerApi.meshNetwork
            if (meshNetwork != null) {
                networkReady = true
                nextGatewayAddr = ColdChainKeys.GATEWAY_START_ADDR
                nextSensorAddr = ColdChainKeys.SENSOR_START_ADDR
                tvDeviceList.text = "暂无设备"
                tvProvisionedNodes.text = "暂无已配网节点"
                log("网络已重置")
            } else {
                networkReady = false
                log("重置失败")
            }
        } catch (e: Exception) { log("重置失败: ${e.message}") }
        updateUi()
    }

    // ─── BLE Scanning ───

    private fun startBleScan() {
        if (!networkReady) { toast("请先创建网络"); return }
        if (isScanning) return
        isScanning = true
        btnScan.isEnabled = false
        btnScan.text = "扫描中..."
        tvStatus.text = "正在扫描未配网设备... (15秒)"
        foundDevices.clear()
        refreshDeviceList()

        val scanner = BluetoothLeScannerCompat.getScanner()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setUseHardwareBatchingIfSupported(false)
            .build()
        val filters = listOf(ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleMeshManager.MESH_PROVISIONING_UUID))
            .build())

        currentScanCallback = object : ScanCallback() {
            override fun onScanResult(cbType: Int, result: ScanResult) {
                val dev = result.device
                val meshUuid = parseMeshUuid(result) ?: return
                if (foundDevices.none { it.device.address == dev.address }) {
                    foundDevices.add(ScanResultWrapper(dev, meshUuid, result.rssi))
                    log("发现: ${dev.name ?: "未知"} [${dev.address}] RSSI=${result.rssi}")
                    refreshDeviceList()
                }
            }
            override fun onScanFailed(errorCode: Int) {
                handler.post { stopUiScan("扫描失败(error=$errorCode)") }
            }
        }
        scanner.startScan(filters, settings, currentScanCallback!!)
        handler.postDelayed({ stopUiScan("扫描完成，共${foundDevices.size}个设备") }, 15000)
    }

    private fun stopUiScan(msg: String) {
        isScanning = false
        btnScan.isEnabled = true
        btnScan.text = "扫描 BLE 设备"
        tvStatus.text = msg
        currentScanCallback?.let { BluetoothLeScannerCompat.getScanner().stopScan(it) }
        currentScanCallback = null
    }

    private fun refreshDeviceList() {
        handler.post {
            if (foundDevices.isEmpty()) {
                tvDeviceList.text = "(暂无发现设备，点击扫描)"
                return@post
            }
            tvDeviceList.text = foundDevices.mapIndexed { i, w ->
                "┌─ [${i}] ${w.device.name ?: "未配网设备"}\n" +
                "│  MAC: ${w.device.address}  RSSI: ${w.rssi} dBm\n" +
                "└─ → 点击配网\n"
            }.joinToString("\n")
        }
    }

    // ─── Provisioning ───

    private fun onDeviceClicked(index: Int) {
        if (index !in foundDevices.indices) return
        val wrapper = foundDevices[index]
        val devName = wrapper.device.name ?: ""
        // 地址分配规则（参照 gateway_group_analysis.md）:
        //   1. 设备名以 "Gateway_" 开头 → 网关 (0x0002~0x000A)
        //   2. 设备名以 "SENSOR_" 开头 → 传感器 (0x0011~)
        //   3. 尚无任何网关 → 视为网关（兼容 ESP-BLE-MESH 默认名）
        //   4. 已有网关 → 传感器
        val isGatewayByName = devName.startsWith("Gateway_")
        val isSensorByName = devName.startsWith("SENSOR_")
        val addr = when {
            isGatewayByName -> nextGatewayAddr
            isSensorByName -> nextSensorAddr
            !hasAnyGateway() -> nextGatewayAddr  // 首个设备默认当网关
            else -> nextSensorAddr
        }
        val role = when {
            isGatewayByName -> "网关"
            isSensorByName -> "传感器"
            !hasAnyGateway() -> "网关(首个设备)"
            else -> "传感器"
        }
        log("地址分配: ${devName} → ${role} 0x${Integer.toHexString(addr).uppercase()}")
        provisionDevice(wrapper, addr)
    }

    /** 是否已有任何网关节点 */
    private fun hasAnyGateway(): Boolean {
        meshNetwork?.nodes?.forEach { if (ColdChainKeys.isGatewayAddr(it.unicastAddress)) return true }
        return false
    }

    private fun hasGateway(): Boolean = hasAnyGateway()

    private fun provisionDevice(wrapper: ScanResultWrapper, unicastAddr: Int) {
        if (isConnecting) { toast("正在连接中"); return }
        val name = wrapper.device.name ?: "未配网设备"
        log("配网: $name → 0x${Integer.toHexString(unicastAddr).uppercase()}")
        tvStatus.text = "正在连接 $name..."
        pendingDevice = wrapper
        pendingUnicastAddr = unicastAddr
        isConnecting = true
        capabilitiesReceived = false
        provisioningStarted = false
        setupMeshCallbacks()
        setupProvisioningCallbacks()
        bleMeshManager.connect(wrapper.device).retry(3, 200).enqueue()
        handler.postDelayed({
            if (isConnecting) {
                log("配网超时(30s)")
                bleMeshManager.disconnect().enqueue()
                isConnecting = false
                tvStatus.text = "配网超时，请重试"
            }
        }, 30000)
    }

    // ─── BLE Callbacks ───

    private val bleCallbacks = object : BleMeshManagerCallbacks {
        override fun onDeviceConnecting(device: BluetoothDevice) { log("连接中: ${device.address}") }
        override fun onDeviceConnected(device: BluetoothDevice) {
            log("已连接: ${device.address}")
            tvStatus.text = "已连接，发现服务中..."
        }
        override fun onDeviceReady(device: BluetoothDevice) {
            log("设备就绪: ${device.address}")
            if (isReconnectingForConfig && pendingConfigNode != null) {
                // 分支A: Proxy重连后配置
                isReconnectingForConfig = false
                val node = pendingConfigNode!!
                pendingConfigNode = null
                tvStatus.text = "Proxy OK，配置中..."
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        configurator.configure(meshManagerApi, meshNetwork, node)
                    } catch (e: Exception) { log("Config异常: ${e.message}") }
                }
                return
            }
            // 分支B: 初次配网
            if (!isConnecting || pendingDevice == null) return
            tvStatus.text = "已连接，启动配网..."
            try {
                meshNetwork!!.assignUnicastAddress(pendingUnicastAddr)
                meshManagerApi.identifyNode(pendingDevice!!.uuid, 10)
                handler.postDelayed({
                    if (isConnecting) startProvisioningNow()
                }, 2000)
            } catch (e: Exception) {
                log("配网准备异常: ${e.message}")
                isConnecting = false
            }
        }
        override fun onDeviceDisconnecting(device: BluetoothDevice) { log("断开中: ${device.address}") }
        override fun onDeviceDisconnected(device: BluetoothDevice) {
            log("已断开: ${device.address}")
            isConnecting = false; provisioningStarted = false
        }
        override fun onDeviceNotSupported(device: BluetoothDevice) {
            log("设备不支持: ${device.address}")
            toast("设备不支持 Mesh")
        }
        override fun onLinkLossOccurred(device: BluetoothDevice) { isConnecting = false }
        override fun onServicesDiscovered(device: BluetoothDevice, optional: Boolean) {}
        override fun onBondingRequired(device: BluetoothDevice) {}
        override fun onBonded(device: BluetoothDevice) {}
        override fun onBondingFailed(device: BluetoothDevice) { isConnecting = false }
        override fun onError(device: BluetoothDevice, message: String, code: Int) {
            log("BLE错误: $message"); isConnecting = false
        }
        override fun onDataReceived(device: BluetoothDevice, mtu: Int, pdu: ByteArray) {
            meshManagerApi.handleNotifications(mtu, pdu)
        }
        override fun onDataSent(device: BluetoothDevice, mtu: Int, pdu: ByteArray) {
            meshManagerApi.handleWriteCallbacks(mtu, pdu)
        }
    }

    // ─── Provisioning Callbacks ───

    private fun setupProvisioningCallbacks() {
        meshManagerApi.setProvisioningStatusCallbacks(object : MeshProvisioningStatusCallbacks {
            override fun onProvisioningStateChanged(node: UnprovisionedMeshNode, state: ProvisioningState.States, data: ByteArray?) {
                handler.post {
                    tvStatus.text = "配网: $state"
                    if (state == ProvisioningState.States.PROVISIONING_CAPABILITIES) {
                        capabilitiesReceived = true
                        handler.postDelayed({ startProvisioningNow() }, 500)
                    }
                }
            }
            override fun onProvisioningFailed(node: UnprovisionedMeshNode, state: ProvisioningState.States, data: ByteArray?) {
                isConnecting = false
                handler.post { tvStatus.text = "配网失败: $state"; toast("配网失败") }
            }
            override fun onProvisioningCompleted(node: ProvisionedMeshNode, state: ProvisioningState.States, data: ByteArray?) {
                val addr = node.unicastAddress
                val isGw = ColdChainKeys.isGatewayAddr(addr)
                val role = if (isGw) "网关" else "传感器"
                log("配网完成: ${role} 0x${Integer.toHexString(addr).uppercase()}")
                isConnecting = false
                bleMeshManager.disconnect().enqueue()
                handler.post {
                    tvStatus.text = "配网完成: ${role} 0x${Integer.toHexString(addr).uppercase()}"
                    toast("配网成功!")
                    if (isGw) nextGatewayAddr = addr + 1 else nextSensorAddr = addr + 1
                    updateUi()
                }
                // 延迟重连→Proxy→Config
                if (meshNetwork != null && pendingDevice != null) {
                    val cfgNode = node
                    configurator.setCallback(object : ColdChainNodeConfigurator.ConfigCallback {
                        override fun onConfigProgress(step: String?, msg: String?) {
                            handler.post { tvStatus.text = "配置: $step" }
                        }
                        override fun onConfigComplete(nodeAddr: Int, success: Boolean) {
                            log("配置完成 0x${Integer.toHexString(nodeAddr)} success=$success")
                            bleMeshManager.disconnect().enqueue()
                            handler.post {
                                tvStatus.text = if (success) "节点就绪" else "配置异常"
                                updateUi()
                            }
                        }
                    })
                    handler.postDelayed({
                        isReconnectingForConfig = true
                        pendingConfigNode = cfgNode
                        tvStatus.text = "重连 Proxy..."
                        bleMeshManager.connect(pendingDevice!!.device).retry(3, 300).enqueue()
                    }, 2000)
                }
            }
        })
    }

    private fun startProvisioningNow() {
        if (!isConnecting || pendingDevice == null || meshNetwork == null) return
        if (provisioningStarted) return
        provisioningStarted = true
        try {
            meshNetwork!!.assignUnicastAddress(pendingUnicastAddr)
            val provisionNode = getInternalMeshNode()
            val addrHex = Integer.toHexString(pendingUnicastAddr).uppercase()
            if (provisionNode != null) {
                provisionNode.nodeName = "设备-$addrHex"
                tvStatus.text = "正在配网 0x$addrHex..."
                meshManagerApi.startProvisioning(provisionNode)
            } else {
                val manualNode = createAndInitNode()
                tvStatus.text = "正在配网 0x$addrHex..."
                meshManagerApi.startProvisioning(manualNode)
            }
        } catch (e: Exception) {
            log("配网异常: ${e.message}")
            isConnecting = false
        }
    }

    private fun getInternalMeshNode(): UnprovisionedMeshNode? {
        return try {
            val f = MeshManagerApi::class.java.getDeclaredField("mMeshProvisioningHandler")
            f.isAccessible = true
            val handler = f.get(meshManagerApi)
            val m = handler.javaClass.getDeclaredMethod("getMeshNode")
            m.isAccessible = true
            m.invoke(handler) as? UnprovisionedMeshNode
        } catch (e: Exception) { null }
    }

    private fun createAndInitNode(): UnprovisionedMeshNode {
        val node = UnprovisionedMeshNode(pendingDevice!!.uuid)
        meshNetwork!!.primaryNetworkKey?.let {
            node.networkKey = it.txNetworkKey
            node.keyIndex = it.keyIndex
        }
        node.flags = byteArrayOf(meshNetwork!!.provisioningFlags.toByte())
        node.ivIndex = ByteBuffer.allocate(4).putInt(meshNetwork!!.ivIndex.ivIndex).array()
        node.ttl = meshNetwork!!.globalTtl
        return node
    }

    // ─── Mesh Callbacks ───

    private fun setupMeshCallbacks() {
        meshManagerApi.setMeshStatusCallbacks(object : MeshStatusCallbacks {
            override fun onTransactionFailed(dst: Int, timer: Boolean) {}
            override fun onUnknownPduReceived(src: Int, payload: ByteArray) {
                log("收到未知PDU: src=0x${Integer.toHexString(src)}, len=${payload.size}")
            }
            override fun onBlockAcknowledgementProcessed(dst: Int, msg: no.nordicsemi.android.mesh.transport.ControlMessage) {}
            override fun onBlockAcknowledgementReceived(src: Int, msg: no.nordicsemi.android.mesh.transport.ControlMessage) {}
            override fun onHeartbeatMessageReceived(src: Int, msg: no.nordicsemi.android.mesh.transport.ControlMessage) {}
            override fun onMeshMessageProcessed(dst: Int, msg: no.nordicsemi.android.mesh.transport.MeshMessage) {}
            override fun onMeshMessageReceived(src: Int, msg: no.nordicsemi.android.mesh.transport.MeshMessage) {
                // ⭐ 转发 Vendor Model 消息到数据仓库（传感器数据→仪表盘）
                handleVendorMessage(src, msg)
            }
            override fun onMessageDecryptionFailed(layer: String, error: String) {
                log("解密失败: $layer - $error")
            }
        })
        if (meshManagerCallbacks == null) {
            meshManagerCallbacks = object : MeshManagerCallbacks {
                override fun onNetworkLoaded(network: MeshNetwork) {}
                override fun onNetworkUpdated(network: MeshNetwork) {}
                override fun onNetworkLoadFailed(error: String) {}
                override fun onNetworkImported(network: MeshNetwork) {}
                override fun onNetworkImportFailed(error: String) {}
                override fun sendProvisioningPdu(node: UnprovisionedMeshNode, pdu: ByteArray) {
                    bleMeshManager.sendPdu(pdu)
                }
                override fun onMeshPduCreated(pdu: ByteArray) {
                    bleMeshManager.sendPdu(pdu)
                }
                override fun getMtu(): Int = bleMeshManager.maximumPacketSize
            }
            meshManagerApi.setMeshManagerCallbacks(meshManagerCallbacks!!)
        }
    }

    /**
     * 处理收到的 Vendor Model 消息 → 解析 → 存入 Room DB → 仪表盘可见
     */
    private fun handleVendorMessage(src: Int, msg: no.nordicsemi.android.mesh.transport.MeshMessage) {
        try {
            if (msg !is VendorModelMessageStatus) return
            val opCode = msg.opCode
            // 只处理我们定义的 Vendor OpCode (0xC1~0xC6)
            if (opCode !in 0xC1..0xC6) return
            val payload = msg.accessPayload ?: return
            val nodeName = getNodeName(src)
            log("收到Vendor消息: src=0x${Integer.toHexString(src)}, opCode=0x${Integer.toHexString(opCode)}, len=${payload.size}")
            lifecycleScope.launch(Dispatchers.IO) {
                ColdChainSensorModelHandler.handleVendorMessage(src, opCode, payload, nodeName, meshDataRepository)
            }
        } catch (_: Exception) {}
    }

    private fun getNodeName(src: Int): String {
        val node = meshNetwork?.getNode(src)
        return node?.nodeName ?: "NODE_${Integer.toHexString(src).uppercase()}"
    }

    // ─── UI ───

    private fun updateUi() {
        handler.post {
            if (networkReady && meshNetwork != null) {
                tvNetworkInfo.text = buildString {
                    append("网络就绪\n")
                    append("Provisioner: 0x0001(库默认)\n")
                    append("下个网关: 0x${Integer.toHexString(nextGatewayAddr).uppercase()}")
                    if (nextGatewayAddr > ColdChainKeys.GATEWAY_END_ADDR) append(" (已满)")
                    append("\n")
                    append("下个传感器: 0x${Integer.toHexString(nextSensorAddr).uppercase()}\n")
                    append("AppKey[${ColdChainKeys.APP_KEY_INDEX}]: 0123456789ABCDEF...\n")
                    append("Group: 0xC001/0xC002/0xC003\n")
                }
                btnCreateNetwork.isEnabled = false
                btnScan.isEnabled = true
            } else {
                tvNetworkInfo.text = "未创建网络\n点击「创建网络」开始"
                btnCreateNetwork.isEnabled = true
                btnScan.isEnabled = false
            }
            // 已配网节点
            val nodes = meshNetwork?.nodes
            if (nodes.isNullOrEmpty()) {
                tvProvisionedNodes.text = "暂无已配网节点"
            } else {
                tvProvisionedNodes.text = nodes.joinToString("\n") { n ->
                    val addr = n.unicastAddress
                    val type = when {
                        ColdChainKeys.isGatewayAddr(addr) -> "【网关】"
                        ColdChainKeys.isSensorAddr(addr) -> "【传感器】"
                        else -> "【其他】"
                    }
                    "$type 0x${Integer.toHexString(addr).uppercase()} | ${n.uuid ?: "未知"}"
                }
            }
        }
    }

    private fun log(msg: String) {
        Log.i("ColdChainV5", msg)
        handler.post {
            val current = tvLog.text?.toString() ?: ""
            val lines = ("$msg\n$current").split("\n").take(50)
            tvLog.text = lines.joinToString("\n")
        }
    }

    private fun toast(msg: String) {
        handler.post { Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show() }
    }

    private fun parseMeshUuid(result: ScanResult): UUID? {
        return try {
            val svcUuid = ParcelUuid(BleMeshManager.MESH_PROVISIONING_UUID)
            val svcData = result.scanRecord?.getServiceData(svcUuid) ?: return null
            if (svcData.size < 16) return null
            var msb = 0L; var lsb = 0L
            for (i in 0..7) msb = (msb shl 8) or (svcData[i].toLong() and 0xFF)
            for (i in 8..15) lsb = (lsb shl 8) or (svcData[i].toLong() and 0xFF)
            UUID(msb, lsb)
        } catch (_: Exception) { null }
    }

    data class ScanResultWrapper(val device: BluetoothDevice, val uuid: UUID, val rssi: Int)
}
