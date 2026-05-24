package no.nordicsemi.android.nrfmesh.coldchain.v5.data.remote

import no.nordicsemi.android.nrfmesh.coldchain.v5.data.model.GatewayStatus
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.model.SensorNodeState
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 网关 HTTP Server API 接口定义
 * 
 * 与 ESP32 网关 Web Server (web/index.html) 互补
 */
interface GatewayApiService {

    /** 获取网关状态（心跳、角色、在线状态） */
    @GET("api/gateway/status")
    suspend fun getGatewayStatus(): Response<GatewayStatus>

    /** 获取所有节点列表 */
    @GET("api/nodes/list")
    suspend fun getNodeList(): Response<List<SensorNodeState>>

    /** 获取指定节点详情 */
    @GET("api/nodes/{addr}")
    suspend fun getNodeDetail(@Path("addr") addr: Int): Response<SensorNodeState>

    /** 批量获取节点状态 */
    @GET("api/nodes/status")
    suspend fun getNodesStatus(
        @Query("addrs") addrs: String  // 逗号分隔的地址列表
    ): Response<List<SensorNodeState>>

    /** 获取审计数据 */
    @GET("api/audit/records")
    suspend fun getAuditRecords(
        @Query("nodeAddr") nodeAddr: Int? = null,
        @Query("startTime") startTime: Long? = null,
        @Query("endTime") endTime: Long? = null
    ): Response<AuditRecordsResponse>

    /** Hash Chain 验证 */
    @GET("api/audit/verify")
    suspend fun verifyHashChain(
        @Query("nodeAddr") nodeAddr: Int,
        @Query("startSeq") startSeq: Int? = null
    ): Response<HashChainVerifyResponse>

    /** 多网关 received_by 证据 */
    @GET("api/audit/received-by")
    suspend fun getReceivedByEvidence(
        @Query("batchId") batchId: Int
    ): Response<List<ReceivedByEvidence>>
}

/**
 * 审计记录响应
 */
data class AuditRecordsResponse(
    val records: List<AuditRecord>,
    val totalCount: Int
)

data class AuditRecord(
    val seqNum: Int,
    val timestamp: Long,
    val nodeAddr: Int,
    val temp: Float,
    val humi: Float,
    val hashPrev: String,
    val hashCurrent: String,
    val ecdsaVerified: Boolean,
    val receivedBy: List<ReceivedByEvidence> = emptyList()
)

data class ReceivedByEvidence(
    val gwId: String,
    val rssi: Int,
    val receivedAt: Long
)

data class HashChainVerifyResponse(
    val nodeAddr: Int,
    val verified: Boolean,
    val totalRecords: Int,
    val brokenAt: Int? = null,      // 断链位置 seqNum
    val message: String = ""
)
