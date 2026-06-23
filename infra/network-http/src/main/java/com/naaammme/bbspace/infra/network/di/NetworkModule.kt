package com.naaammme.bbspace.infra.network.di

import android.content.Context
import com.naaammme.bbspace.infra.crypto.BuvidFetcher
import com.naaammme.bbspace.infra.crypto.DeviceIdentity
import com.naaammme.bbspace.infra.crypto.GuestIdGenerator
import com.naaammme.bbspace.infra.crypto.HwIdGenerator
import com.naaammme.bbspace.infra.crypto.LegalRegionCache
import com.naaammme.bbspace.infra.crypto.RegionCodeCache
import com.naaammme.bbspace.infra.crypto.TicketGenerator
import com.naaammme.bbspace.infra.network.dns.BiliDns
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideDeviceIdentity(@ApplicationContext context: Context): DeviceIdentity {
        return DeviceIdentity(context)
    }

    @Provides
    @Singleton
    fun provideHwIdGenerator(deviceIdentity: DeviceIdentity): HwIdGenerator {
        return HwIdGenerator(deviceIdentity)
    }

    @Provides
    @Singleton
    fun provideBiliDns(
        @ApplicationContext context: Context
    ): BiliDns {
        return BiliDns(context)
    }

    @Provides
    @Singleton
    fun provideOkHttpConnectionPool(): ConnectionPool {
        return ConnectionPool(10, 5, TimeUnit.MINUTES)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        biliDns: BiliDns,
        connectionPool: ConnectionPool
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .dns(biliDns)
            .connectionPool(connectionPool)
            .addInterceptor(BrotliInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideTicketGenerator(
        @ApplicationContext context: Context,
        deviceIdentity: DeviceIdentity,
        okHttpClient: OkHttpClient
    ): TicketGenerator {
        return TicketGenerator(context, deviceIdentity, okHttpClient)
    }

    @Provides
    @Singleton
    fun provideGuestIdGenerator(
        @ApplicationContext context: Context,
        deviceIdentity: DeviceIdentity,
        okHttpClient: OkHttpClient
    ): GuestIdGenerator {
        return GuestIdGenerator(context, deviceIdentity, okHttpClient)
    }

    @Provides
    @Singleton
    fun provideRegionCodeCache(): RegionCodeCache {
        return RegionCodeCache()
    }

    @Provides
    @Singleton
    fun provideLegalRegionCache(
        @ApplicationContext context: Context
    ): LegalRegionCache {
        return LegalRegionCache(context)
    }

    @Provides
    @Singleton
    fun provideBuvidFetcher(
        okHttpClient: OkHttpClient,
        deviceIdentity: DeviceIdentity
    ): BuvidFetcher {
        return BuvidFetcher(okHttpClient, deviceIdentity)
    }
}
