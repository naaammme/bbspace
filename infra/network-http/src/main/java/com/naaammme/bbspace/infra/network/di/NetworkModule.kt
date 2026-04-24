package com.naaammme.bbspace.infra.network.di

import android.content.Context
import com.naaammme.bbspace.infra.crypto.BuvidFetcher
import com.naaammme.bbspace.infra.crypto.DeviceIdentity
import com.naaammme.bbspace.infra.crypto.GuestIdGenerator
import com.naaammme.bbspace.infra.crypto.LegalRegionCache
import com.naaammme.bbspace.infra.crypto.RegionCodeCache
import com.naaammme.bbspace.infra.crypto.TicketGenerator
import com.naaammme.bbspace.infra.network.dns.BiliDns
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
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
    fun provideBiliDns(): BiliDns {
        return BiliDns()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(biliDns: BiliDns): OkHttpClient {
        return OkHttpClient.Builder()
            .dns(biliDns)
            .addInterceptor(BrotliInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideTicketGenerator(
        @ApplicationContext context: Context,
        deviceIdentity: DeviceIdentity
    ): TicketGenerator {
        return TicketGenerator(context, deviceIdentity)
    }

    @Provides
    @Singleton
    fun provideGuestIdGenerator(
        @ApplicationContext context: Context,
        deviceIdentity: DeviceIdentity
    ): GuestIdGenerator {
        return GuestIdGenerator(context, deviceIdentity)
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
