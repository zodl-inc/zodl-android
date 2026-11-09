package com.flexa.core.view

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.flexa.core.Flexa
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

/**
 * The one Coil [ImageLoader] every Flexa image goes through (merchant logos, brand and asset icons).
 * Images come from Flexa's own hosts, not from the API the app routes through
 * [com.flexa.core.shared.FlexaClientConfiguration.okHttpClientCustomizer], so a download is a direct
 * connection: [DirectConnectionGate] refuses it while [Flexa.allowsDirectConnections] is false, and the
 * caller's placeholder or error image shows instead. Images already in the memory or disk cache still show.
 */
object FlexaImageLoader {
    @Volatile
    private var loader: ImageLoader? = null

    fun get(context: Context): ImageLoader =
        loader ?: synchronized(this) {
            loader ?: build(context.applicationContext).also { loader = it }
        }

    private fun build(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .okHttpClient { OkHttpClient.Builder().addInterceptor(DirectConnectionGate).build() }
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .build()
}

/** Fails a request while [Flexa.allowsDirectConnections] is false, before anything is sent. */
internal object DirectConnectionGate : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!Flexa.allowsDirectConnections()) {
            throw IOException("Direct connections to Flexa are off (private connection or Tor)")
        }
        return chain.proceed(chain.request())
    }
}
