package no.nordicsemi.android.nrfmesh.coldchain.v5.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.repository.DashboardRepository
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.repository.GatewayHttpRepository
import no.nordicsemi.android.nrfmesh.coldchain.v5.data.repository.MeshDataRepository
import no.nordicsemi.android.nrfmesh.coldchain.v5.mesh.MeshIntegrationManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideDashboardRepository(
        meshDataRepository: MeshDataRepository,
        gatewayHttpRepository: GatewayHttpRepository
    ): DashboardRepository {
        return DashboardRepository(meshDataRepository, gatewayHttpRepository)
    }

    @Provides
    @Singleton
    fun provideMeshIntegrationManager(
        meshDataRepository: MeshDataRepository,
        gatewayHttpRepository: GatewayHttpRepository,
        gatewayApiService: no.nordicsemi.android.nrfmesh.coldchain.v5.data.remote.GatewayApiService
    ): MeshIntegrationManager {
        return MeshIntegrationManager(meshDataRepository, gatewayHttpRepository, gatewayApiService)
    }
}
