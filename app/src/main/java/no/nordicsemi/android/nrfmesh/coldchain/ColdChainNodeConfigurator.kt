package no.nordicsemi.android.nrfmesh.coldchain

import android.os.Handler
import android.os.Looper
import android.util.Log
import no.nordicsemi.android.mesh.MeshManagerApi
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.mesh.transport.ConfigAppKeyAdd
import no.nordicsemi.android.mesh.transport.ConfigModelAppBind
import no.nordicsemi.android.mesh.transport.ConfigModelPublicationSet
import no.nordicsemi.android.mesh.transport.MeshMessage
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode
import java.util.Locale
import java.util.Locale.getDefault

/**
 * 配网后自动配置器
 * 
 * 对刚配网的节点自动执行：AppKey Add → Model App Bind → Publication Set
 * 与网关固件 V2 中 ble_mesh_provision_device() 的 Step 4-5.5 一致
 * 
 * ⭐ 前提条件：Provisioner 必须已设置单播地址（否则 createMeshPdu 会抛异常）
 */
class ColdChainNodeConfigurator {
    private val handler = Handler(Looper.getMainLooper())
    private var callback: ConfigCallback? = null

    interface ConfigCallback {
        fun onConfigProgress(step: String?, message: String?)
        fun onConfigComplete(nodeAddr: Int, success: Boolean)
    }

    fun setCallback(cb: ConfigCallback?) {
        this.callback = cb
    }

    /**
     * 对新配网节点执行自动配置
     * 
     * @param meshManagerApi MeshManagerApi实例（Provisioner地址必须已设置！）
     * @param network        Mesh 网络
     * @param node            刚配网的节点
     */
    fun configure(
        meshManagerApi: MeshManagerApi,
        network: MeshNetwork?,
        node: ProvisionedMeshNode?
    ) {
        if (network == null) {
            log("❌ 网络为空，无法配置")
            callback?.onConfigComplete(0, false)
            return
        }
        if (node == null) {
            log("❌ 节点为空，无法配置")
            callback?.onConfigComplete(0, false)
            return
        }

        // ⭐ 前置检查：确认 Provisioner 地址已设置
        val provisioner = network.selectedProvisioner
        if (provisioner == null || provisioner.provisionerAddress == null) {
            log("❌ Provisioner 未设置地址，无法发送配置消息！")
            notifyProgress("错误", "Provisioner 地址未设置")
            callback?.onConfigComplete(0, false)
            return
        }

        val nodeAddr = node.unicastAddress
        val appKeyIndex = ColdChainKeys.APP_KEY_INDEX
        val netKeyIndex = ColdChainKeys.NET_KEY_INDEX
        val modelId = ColdChainKeys.VENDOR_MODEL_ID
        val pubAddr = ColdChainKeys.GATEWAY_UNICAST_ADDR

        log("═══ 开始配置节点 0x${Integer.toHexString(nodeAddr).uppercase(Locale.getDefault())} ═══")
        log("Provisioner=0x${
            Integer.toHexString(provisioner.provisionerAddress!!).uppercase(getDefault())
        }")

        var allSuccess = true

        // ── Step 1: AppKey Add ──
        try {
            notifyProgress("AppKey添加", "正在添加AppKey到节点...")
            val appKey = network.getAppKey(appKeyIndex)
            val netKey = network.getNetKey(netKeyIndex)
            if (appKey != null && netKey != null) {
                log("Step1: ConfigAppKeyAdd(netKeyIdx=$netKeyIndex, appKeyIdx=$appKeyIndex)")
                val appKeyAdd = ConfigAppKeyAdd(netKey, appKey)
                sendConfigMessage(meshManagerApi, nodeAddr, appKeyAdd)
                log("✓ Step1 已发送，等待响应...")
            } else {
                log("✗ Step1 跳过: appKey=$appKey, netKey=$netKey")
                allSuccess = false
            }
        } catch (e: Exception) {
            log("✗ Step1 异常: ${e.message}")
            allSuccess = false
        }

        delay(3000)

        // ── Step 2: Model App Bind (Vendor Model) ──
        try {
            notifyProgress("Model绑定", "正在绑定Vendor Model...")
            log("Step2: ConfigModelAppBind(element=0x${Integer.toHexString(nodeAddr)}, model=0x${Integer.toHexString(modelId)}, keyIdx=$appKeyIndex)")
            val modelBind = ConfigModelAppBind(
                nodeAddr,
                modelId,
                appKeyIndex
            )
            sendConfigMessage(meshManagerApi, nodeAddr, modelBind)
            log("✓ Step2 已发送，等待响应...")
        } catch (e: Exception) {
            log("✗ Step2 异常: ${e.message}")
            allSuccess = false
        }

        delay(3000)

        // ── Step 3: Publication Set ──
        try {
            notifyProgress("发布地址", "正在配置Publication地址...")
            log("Step3: ConfigModelPublicationSet(pubAddr=0x${
                Integer.toHexString(pubAddr).uppercase(getDefault())
            })")
            val pubSet = ConfigModelPublicationSet(
                nodeAddr,
                pubAddr,
                appKeyIndex,
                false,   // credentialFlag
                7,       // publishTtl
                0,       // publicationSteps
                0,       // publicationResolution
                2,       // retransmitCount
                3,       // retransmitIntervalSteps
                modelId
            )
            sendConfigMessage(meshManagerApi, nodeAddr, pubSet)
            log("✓ Step3 已发送，等待响应...")
        } catch (e: Exception) {
            log("✗ Step3 异常: ${e.message}")
            allSuccess = false
        }

        delay(1000)

        log("═══ 配置完成: ${if (allSuccess) "成功" else "部分失败"} ═══")
        notifyProgress(null, "节点 0x${Integer.toHexString(nodeAddr).uppercase(Locale.getDefault())} "
                + (if (allSuccess) "配置完成" else "配置部分失败"))
        callback?.onConfigComplete(nodeAddr, allSuccess)
    }

    private fun sendConfigMessage(
        meshManagerApi: MeshManagerApi,
        dstAddr: Int,
        message: MeshMessage
    ) {
        try {
            // createMeshPdu是MeshManagerApi的方法，不是MeshNetwork的
            meshManagerApi.createMeshPdu(dstAddr, message)
            log(
                ("发送配置消息: " + message.javaClass.getSimpleName()
                        + " -> 0x" + Integer.toHexString(dstAddr).uppercase(Locale.getDefault()))
            )
        } catch (e: Exception) {
            log("发送配置消息失败: " + e.message)
        }
    }

    private fun delay(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (ignored: InterruptedException) {
        }
    }

    private fun log(msg: String) {
        Log.i(TAG, msg)
    }

    private fun notifyProgress(step: String?, msg: String?) {
        log("[" + step + "] " + msg)
        if (callback != null) {
            handler.post(Runnable { callback!!.onConfigProgress(step, msg) })
        }
    }

    companion object {
        private const val TAG = "ColdChainConfig"
    }
}
