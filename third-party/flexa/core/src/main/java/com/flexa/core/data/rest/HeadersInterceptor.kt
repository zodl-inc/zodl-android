package com.flexa.core.data.rest

import com.flexa.core.data.data.TokenProvider
import com.flexa.core.minutesBetween
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.net.HttpURLConnection
import java.time.Instant
import java.util.UUID

internal const val MINIMUM_REFRESH_MINUTES = 5

internal class HeadersInterceptor(
    private val tokenProvider: TokenProvider,
    private val keepAlive: Boolean = false
) : FlexaInterceptor() {

    private val mutex = tokenProvider.mutex

    override fun intercept(chain: Interceptor.Chain): Response = runBlocking {
        mutex.withLock {
            val tokenExpiration = tokenProvider.getTokenExpiration()
            val tokenExpirationMinutes = Instant.now().minutesBetween(tokenExpiration)

            if (tokenExpirationMinutes <= MINIMUM_REFRESH_MINUTES) {
                tokenProvider.getRefreshToken(headersBundle)
            }
        }
        val token = tokenProvider.getToken()
        val request = newRequestWithAccessToken(chain.request(), token)
        var response = chain.proceed(request)

        if (!keepAlive) checkResponse(response)

        if (response.code == HttpURLConnection.HTTP_FORBIDDEN ||
            response.code == HttpURLConnection.HTTP_UNAUTHORIZED
        ) {
            response.close()
            val newAccessToken = tokenProvider.getRefreshToken(headersBundle)
            response = chain.proceed(newRequestWithAccessToken(request, newAccessToken))
        }

        response
    }

    private fun newRequestWithAccessToken(
        request: Request,
        token: String
    ): Request {
        val tokenText = ":${token}"
        val tokenBase64 = android.util.Base64.encodeToString(
            tokenText.toByteArray(), android.util.Base64.NO_WRAP
        )

        return request.newBuilder()
            .header("Flexa-App", headersBundle.appName)
            .header("Flexa-Version", headersBundle.version)
            .header("User-Agent", headersBundle.userAgent)
            .header("Authorization", "Basic $tokenBase64")
            .header("client-trace-id", UUID.randomUUID().toString())
            .run {
                if (!keepAlive) {
                    this.header("Accept", "application/vnd.flexa+json")
                } else {
                    this.header("Accept", "text/event-stream")
                        .header("Cache-Control", "no-cache")
                        .header("Connection", "keep-alive")
                }
            }
            .build()
    }
}
