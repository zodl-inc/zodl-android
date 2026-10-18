package com.flexa.core.data.rest

import com.flexa.core.data.data.TokenProvider
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.UUID

internal class LoginInterceptor(
    private val tokenProvider: TokenProvider
) : FlexaInterceptor() {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider.getToken()
        val request = newRequestWithAccessToken(chain.request(), token)
        val response = chain.proceed(request)
        checkResponse(response)
        return response
    }

    private fun newRequestWithAccessToken(
        request: Request,
        token: String
    ): Request {
        val tokenBase64 = android.util.Base64.encodeToString(
            ":${token}".toByteArray(), android.util.Base64.NO_WRAP
        )

        return request.newBuilder()
            .header("Accept", "application/vnd.flexa+json")
            .header("Flexa-App", headersBundle.appName)
            .header("Flexa-Version", headersBundle.version)
            .header("User-Agent", headersBundle.userAgent)
            .header("Content-Type", "application/json")
            .header("Authorization", "Basic $tokenBase64")
            .header("client-trace-id", UUID.randomUUID().toString())
            .build()
    }
}
