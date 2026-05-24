package no.nordicsemi.android.nrfmesh.coldchain.v5.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: SensorRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<SensorRecordEntity>)

    @Query("SELECT * FROM sensor_records WHERE nodeAddr = :nodeAddr ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByNode(nodeAddr: Int, limit: Int = 100): List<SensorRecordEntity>

    @Query("SELECT * FROM sensor_records WHERE nodeAddr = :nodeAddr AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getByNodeAndTime(nodeAddr: Int, startTime: Long, endTime: Long): List<SensorRecordEntity>

    @Query("SELECT * FROM sensor_records ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<SensorRecordEntity>>

    @Query("SELECT * FROM sensor_records WHERE nodeAddr = :nodeAddr ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestByNode(nodeAddr: Int): SensorRecordEntity?

    @Query("SELECT * FROM sensor_records WHERE nodeAddr = :nodeAddr ORDER BY timestamp DESC LIMIT :limit")
    fun observeByNode(nodeAddr: Int, limit: Int = 100): Flow<List<SensorRecordEntity>>

    @Query("SELECT DISTINCT nodeAddr FROM sensor_records")
    suspend fun getAllNodeAddrs(): List<Int>

    @Query("DELETE FROM sensor_records WHERE timestamp < :beforeTime")
    suspend fun deleteOlderThan(beforeTime: Long)

    @Query("SELECT COUNT(*) FROM sensor_records")
    fun observeCount(): Flow<Int>

    /** 获取各节点最新记录（用于仪表盘聚合） */
    @Query("""
        SELECT * FROM sensor_records s1 
        WHERE s1.id = (
            SELECT s2.id FROM sensor_records s2 
            WHERE s2.nodeAddr = s1.nodeAddr 
            ORDER BY s2.timestamp DESC LIMIT 1
        )
        ORDER BY s1.timestamp DESC
    """)
    suspend fun getLatestPerNode(): List<SensorRecordEntity>
}
