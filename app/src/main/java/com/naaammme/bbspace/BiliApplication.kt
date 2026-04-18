package com.naaammme.bbspace

import android.app.Application
import android.os.Build.VERSION.SDK_INT
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.util.DebugLogger
import okio.Path.Companion.toOkioPath
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.common.UserAgentBuilder
import com.naaammme.bbspace.infra.crypto.DeviceIdentity
import dagger.hilt.android.HiltAndroidApp
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class BiliApplication : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var appInitializer: AppInitializer

    @Inject
    lateinit var deviceIdentity: DeviceIdentity

    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun onCreate() {
        super.onCreate()
        Logger.init(BuildConfig.DEBUG)
        setupGlobalExceptionHandler()
        appInitializer.initialize()
    }

    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Logger.e("UncaughtException", throwable) { "线程 ${thread.name} 发生未捕获异常" }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val grpcUa = UserAgentBuilder.buildGrpcUserAgent(deviceIdentity.model, deviceIdentity.osVer)
        val imageClient = okHttpClient.newBuilder()
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.url.host.endsWith("hdslb.com")) {
                    chain.proceed(
                        req.newBuilder()
                            .header("User-Agent", grpcUa)
                            .build()
                    )
                } else {
                    chain.proceed(req)
                }
            }
            .build()

        return ImageLoader.Builder(context)
            .components {
                if (SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(OkHttpNetworkFetcherFactory(callFactory = imageClient))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(100 * 1024 * 1024)
                    .build()
            }
            .crossfade(150)
            .apply {
                if (BuildConfig.DEBUG) {
                    logger(DebugLogger())
                }
            }
            .build()
    }
}
