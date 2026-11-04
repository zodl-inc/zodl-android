package com.flexa.core.data.data

import android.content.Intent
import android.util.Log
import com.flexa.BuildConfig
import com.flexa.core.Flexa
import com.flexa.core.data.rest.HeadersBundle
import com.flexa.core.data.rest.RestRepository
import com.flexa.core.data.rest.RestRepository.Companion.json
import com.flexa.core.data.rest.buildFlexaClient
import com.flexa.core.data.storage.SecuredPreferences
import com.flexa.core.domain.data.ITokenProvider
import com.flexa.core.entity.TokenPatch
import com.flexa.core.shared.FlexaConstants
import com.flexa.core.shared.FlexaConstants.Companion.EMPTY
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private const val TOKEN_ERROR_COUNTER = 3

internal class TokenProvider(
    internal val preferences: SecuredPreferences,
    private val tokenKey: String
) : ITokenProvider {

    val mutex = Mutex()
    private val errorCounter = AtomicInteger(0)
    private val tokenExpiration = AtomicLong(Long.MIN_VALUE)
    private val token = AtomicReference(EMPTY)

    private val client
        get() = OkHttpClient().newBuilder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                redactHeader("Authorization")
                level = when (BuildConfig.DEBUG) {
                    true -> HttpLoggingInterceptor.Level.BODY
                    else -> HttpLoggingInterceptor.Level.NONE
                }
            }
            ).buildFlexaClient()

    override fun dropCache() {
        errorCounter.set(0)
        tokenExpiration.set(Long.MIN_VALUE)
        token.set(EMPTY)
    }

    override fun getTokenExpiration(): Long {
        return tokenExpiration.updateAndGet { time ->
            if (time == Long.MIN_VALUE)
                preferences.getLongSynchronously(FlexaConstants.TOKEN_EXPIRATION)
            else
                time
        }
    }

    override fun getToken(): String {
        return token.updateAndGet {
            it.ifBlank { preferences.getStringSynchronously(tokenKey) ?: EMPTY }
        }
    }

    override fun saveToken(token: String) {
        this.token.set(token)
        preferences.edit()
            .putString(tokenKey, token)
            .apply()
    }

    override fun getRefreshToken(headersBundle: HeadersBundle): String {
        val url = HttpUrl.Builder()
            .scheme(RestRepository.SCHEME)
            .host(RestRepository.host)
            .addPathSegment("tokens").build()

        val verifier = preferences.getStringSynchronously(FlexaConstants.VERIFIER)
        val newVerifier = PikSeeProvider.getCodeVerifier()
        val challenge = PikSeeProvider.getCodeChallenge(newVerifier)

        val body = buildJsonObject {
            put("challenge", challenge)
            put("verifier", verifier)
        }.run { toString().toRequestBody(RestRepository.mediaType) }

        val oldToken = getToken()
        val tokenBase64 = android.util.Base64.encodeToString(
            ":${oldToken}".toByteArray(), android.util.Base64.NO_WRAP
        )

        val request: Request = Request.Builder().url(url)
            .header("Accept", "application/vnd.flexa+json")
            .header("Flexa-App", headersBundle.appName)
            .header("Flexa-Version", headersBundle.version)
            .header("User-Agent", headersBundle.userAgent)
            .header("Authorization", "Basic $tokenBase64")
            .header("client-trace-id", UUID.randomUUID().toString())
            .post(body).build()

        val token = try {
            val response = client.newCall(request).execute()
            val raw = response.body?.string().toString()
            val tokenResponse = try {
                json.decodeFromString<TokenPatch>(raw)
            } catch (e: Exception) {
                TokenPatch("", "", "", 0)
            }
            val token = tokenResponse.value
            if (token.isNotBlank()) {
                saveToken(token)
                setTokenExpiration(tokenResponse.expiration)
                errorCounter.set(0)
                preferences.saveStringSynchronous(FlexaConstants.VERIFIER, newVerifier)
                Flexa.context?.let {
                    val intent = Intent(preferences.getPublishableKey())
                    intent.putExtra(FlexaConstants.TOKEN, true)
                    it.sendBroadcast(intent)
                }
            } else {
                if (errorCounter.incrementAndGet() >= TOKEN_ERROR_COUNTER) {
                    Flexa.context?.let {
                        val intent = Intent(preferences.getPublishableKey())
                        intent.putExtra(FlexaConstants.TOKEN, false)
                        it.sendBroadcast(intent)
                    }
                }
            }
            token
        } catch (e: Exception) {
            Log.e("TAG", "getRefreshToken: ", e)
            EMPTY
        }

        Log.d(
            TokenProvider::class.java.simpleName,
            "refreshed token: \uD83C\uDFC1: $token thread: ${Thread.currentThread().id} hash:${hashCode()}"
        )
        return token
    }

    private fun setTokenExpiration(timestamp: Long) {
        tokenExpiration.set(timestamp)
        preferences.edit()
            .putLong(FlexaConstants.TOKEN_EXPIRATION, timestamp)
            .apply()
    }
}
