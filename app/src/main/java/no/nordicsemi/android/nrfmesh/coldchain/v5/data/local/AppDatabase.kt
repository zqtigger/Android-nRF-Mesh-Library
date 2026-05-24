package no.nordicsemi.android.nrfmesh.coldchain.v5.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * V5 App 本地数据库
 * 缓存最近 7 天传感器记录、告警事件、网关心跳
 */
@Database(
    entities = [
        SensorRecordEntity::class,
        AlarmEventEntity::class,
        GatewayHeartbeatEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sensorRecordDao(): SensorRecordDao
    abstract fun alarmEventDao(): AlarmEventDao
    abstract fun gatewayHeartbeatDao(): GatewayHeartbeatDao

    companion object {
        private const val DB_NAME = "coldchain_v5.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
