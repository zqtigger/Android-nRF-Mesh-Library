package no.nordicsemi.android.nrfmesh.coldchain.v5.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.repository.GatewayHttpRepository
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.repository.MeshDataRepository
import java.util.concurrent.TimeUnit

/**
 * 后台数据同步 Worker
 * 定期从 Gateway HTTP 拉取批量数据补全本地缓存
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val gatewayHttpRepository: GatewayHttpRepository,
    private val meshDataRepository: MeshDataRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "SyncWorker started")
        return withContext(Dispatchers.IO) {
            try {
                // 1. 拉取网关状态
                gatewayHttpRepository.fetchGatewayStatus()

                // 2. 拉取节点列表
                val nodes = gatewayHttpRepository.fetchNodeList()
                Log.i(TAG, "Fetched ${nodes.size} nodes from gateway")

                // 3. 清理过期数据（保留 7 天）
                meshDataRepository.cleanOldData()

                Log.i(TAG, "SyncWorker completed successfully")
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "SyncWorker failed: ${e.message}", e)
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "SyncWorker"

        /** 创建定期同步任务（每 30 分钟） */
        fun createPeriodicRequest(): PeriodicWorkRequest {
            return PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
        }

        /** 创建一次性同步 */
        fun createOneTimeRequest(): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
        }
    }
}
