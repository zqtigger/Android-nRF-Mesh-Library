package no.nordicsemi.android.nrfmesh.coldchain.v5.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.local.AlarmEventDao
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.local.AppDatabase
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.local.GatewayHeartbeatDao
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.local.SensorRecordDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun provideSensorRecordDao(db: AppDatabase): SensorRecordDao {
        return db.sensorRecordDao()
    }

    @Provides
    fun provideAlarmEventDao(db: AppDatabase): AlarmEventDao {
        return db.alarmEventDao()
    }

    @Provides
    fun provideGatewayHeartbeatDao(db: AppDatabase): GatewayHeartbeatDao {
        return db.gatewayHeartbeatDao()
    }
}
