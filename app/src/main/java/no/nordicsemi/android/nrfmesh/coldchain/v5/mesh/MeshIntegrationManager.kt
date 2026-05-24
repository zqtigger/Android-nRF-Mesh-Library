package no.nordicsemi.android.nrfmesh.coldchain.v5.mesh

import kotlinx.coroutines.*
import no.nordicsemi.android.mesh.MeshManagerApi
import no.nordicsemi.android.nrfmesh.coldchain.ColdChainKeys
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.local.AppDatabase
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.remote.GatewayApiService
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.repository.GatewayHttpRepository
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.repository.MeshDataRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * V5 Mesh 集成管理器
 * 
 * 将 nRF Mesh Manager API 与 V5 数据层桥接
 * - 监听 Mesh 网络状态变化
 * - 分发 Vendor Message 到 Repository
 * - 初始化 HTTP Repository 连接
 */
@Singleton
class MeshIntegrationManager @Inject constructor(
    private val meshDataRepository: MeshDataRepository,
    private val gatewayHttpRepository: GatewayHttpRepository,
    private val gatewayApiService: GatewayApiService
) {
    private var meshManagerApi: MeshManagerApi? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 绑定 MeshManagerApi（在 Mesh 网络创建后调用） */
    fun bindMeshManager(meshManagerApi: MeshManagerApi) {
        this.meshManagerApi = meshManagerApi
    }

    /**
     * 处理收到的 Vendor Message
     * 在 MeshManagerCallbacks.onMeshMessageReceived 或类似的回调中调用
     */
    fun onVendorMessageReceived(src: Int, opCode: Int, payload: ByteArray) {
        scope.launch {
            val nodeName = getNodeName(src)
            ColdChainSensorModelHandler.handleVendorMessage(
                src, opCode, payload, nodeName, meshDataRepository
            )
        }
    }

    /** 设置网关 HTTP 连接 */
    fun connectGateway(baseUrl: String) {
        gatewayHttpRepository.setBaseUrl(baseUrl, gatewayApiService)
    }

    /** 发送时间同步 */
    fun sendTimeSync() {
        scope.launch {
            val timestamp = System.currentTimeMillis() / 1000
            val payload = ColdChainSensorModelHandler.buildTimeSyncMessage(timestamp)
            // 通过 ConfigMessage 发送到 Group 0xC001
            // meshManagerApi?.createMeshPdu(...)
        }
    }

    private fun getNodeName(src: Int): String {
        val network = meshManagerApi?.meshNetwork
        return if (network != null) {
            val node = network.getNode(src)
            node?.nodeName ?: "NODE_${Integer.toHexString(src).uppercase()}"
        } else {
            "NODE_${Integer.toHexString(src).uppercase()}"
        }
    }

    fun destroy() {
        scope.cancel()
    }
}
