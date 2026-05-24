package no.nordicsemi.android.nrfmesh.coldchain.v5.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GatewayHeartbeatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(heartbeat: GatewayHeartbeatEntity)

    @Query("SELECT * FROM gateway_heartbeats WHERE gwId = :gwId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(gwId: String): GatewayHeartbeatEntity?

    @Query("SELECT * FROM gateway_heartbeats WHERE gwId = :gwId ORDER BY timestamp DESC LIMIT :limit")
    fun observeByGateway(gwId: String, limit: Int = 20): Flow<List<GatewayHeartbeatEntity>>

    @Query("SELECT DISTINCT gwId FROM gateway_heartbeats")
    suspend fun getAllGatewayIds(): List<String>

    @Query("""
        SELECT * FROM gateway_heartbeats g1 
        WHERE g1.id = (
            SELECT g2.id FROM gateway_heartbeats g2 
            WHERE g2.gwId = g1.gwId 
            ORDER BY g2.timestamp DESC LIMIT 1
        )
    """)
    suspend fun getLatestPerGateway(): List<GatewayHeartbeatEntity>
}
