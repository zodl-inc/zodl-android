package com.flexa.core.data.rest

import com.flexa.BuildConfig
import com.flexa.core.Flexa
import com.flexa.core.data.data.TokenProvider
import com.flexa.core.data.storage.SecuredPreferences
import com.flexa.core.shared.FlexaConstants
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File

internal class OkHttpProvider(
    private val preferences: SecuredPreferences
) {

    internal val loginClient: OkHttpClient
        get() {
            val tokenProvider = TokenProvider(preferences, FlexaConstants.PUBLISHABLE_KEY)
            return OkHttpClient().newBuilder()
                .addInterceptor(LoginInterceptor(tokenProvider))
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = when (BuildConfig.DEBUG) {
                        true -> HttpLoggingInterceptor.Level.BODY
                        else -> HttpLoggingInterceptor.Level.NONE
                    }
                }
                )
                .buildFlexaClient()
        }
    internal val client by lazy {
        val tokenProvider = tokenProvider
        OkHttpClient().newBuilder()
            .cache(
                Cache(
                    directory = File(Flexa.requiredContext.cacheDir, "http_cache"),
                    maxSize = 50L * 1024L * 1024L // 50 MiB
                )
            )
            .addInterceptor(HeadersInterceptor(tokenProvider))
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = when (BuildConfig.DEBUG) {
                    true -> HttpLoggingInterceptor.Level.BODY
                    else -> HttpLoggingInterceptor.Level.NONE
                }
            }
            )
            .buildFlexaClient()
    }
    internal val sseClient by lazy {
        val tokenProvider = tokenProvider
        OkHttpClient().newBuilder()
            .addInterceptor(HeadersInterceptor(tokenProvider, true))
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = when (BuildConfig.DEBUG) {
                    true -> HttpLoggingInterceptor.Level.HEADERS
                    else -> HttpLoggingInterceptor.Level.NONE
                }
            }
            )
            .buildFlexaClient()
    }
    internal val tokenProvider by lazy {
        TokenProvider(preferences, FlexaConstants.TOKEN)
    }
}

/** Builds the client after applying [Flexa.okHttpClientCustomizer], so it comes after the SDK's own interceptors. */
internal fun OkHttpClient.Builder.buildFlexaClient(): OkHttpClient =
    apply { Flexa.okHttpClientCustomizer?.invoke(this) }.build()
