package com.geomoby.blescanner.data.di

import android.bluetooth.BluetoothManager
import android.content.Context
import com.geomoby.blescanner.data.repository.BeaconRepositoryImpl
import com.geomoby.blescanner.domain.repository.BeaconRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt dependency injection module for the data layer.
 *
 * Provides Android system services and binds repository implementations
 * to their domain-layer interfaces, enabling constructor injection
 * throughout the app without manual wiring.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    /**
     * Binds [BeaconRepositoryImpl] to the [BeaconRepository] interface.
     *
     * This ensures the presentation layer depends only on the abstraction,
     * keeping the domain layer free of Android framework dependencies
     * and enabling easy test doubles in unit tests.
     */
    @Binds
    @Singleton
    abstract fun bindBeaconRepository(impl: BeaconRepositoryImpl): BeaconRepository

    companion object {

        /**
         * Provides the system [BluetoothManager] for BLE operations.
         *
         * Uses Application context to ensure the manager lives for the
         * entire app lifecycle, avoiding context leaks from Activity references.
         */
        @Provides
        @Singleton
        fun provideBluetoothManager(
            @ApplicationContext context: Context
        ): BluetoothManager {
            return context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        }
    }
}
