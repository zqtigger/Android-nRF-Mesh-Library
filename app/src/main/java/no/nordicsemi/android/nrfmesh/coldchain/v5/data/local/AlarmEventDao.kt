package no.nordicsemi.android.nrfmesh.coldchain.v5.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AlarmEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<AlarmEventEntity>)

    @Query("SELECT * FROM alarm_events ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<AlarmEventEntity>>

    @Query("SELECT * FROM alarm_events WHERE acknowledged = 0 ORDER BY timestamp DESC")
    fun observeUnacknowledged(): Flow<List<AlarmEventEntity>>

    @Query("SELECT * FROM alarm_events WHERE acknowledged = 1 ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAcknowledged(limit: Int = 50): List<AlarmEventEntity>

    @Query("SELECT COUNT(*) FROM alarm_events WHERE acknowledged = 0")
    fun observeUnacknowledgedCount(): Flow<Int>

    @Query("UPDATE alarm_events SET acknowledged = 1 WHERE id = :eventId")
    suspend fun acknowledge(eventId: Long)

    @Query("UPDATE alarm_events SET acknowledged = 1 WHERE nodeAddr = :nodeAddr AND acknowledged = 0")
    suspend fun acknowledgeAllForNode(nodeAddr: Int)

    @Query("DELETE FROM alarm_events WHERE timestamp < :beforeTime")
    suspend fun deleteOlderThan(beforeTime: Long)
}
