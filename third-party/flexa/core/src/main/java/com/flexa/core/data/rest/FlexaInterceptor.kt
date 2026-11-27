package com.flexa.core.data.rest

import android.os.Build
import com.flexa.BuildConfig
import com.flexa.core.Flexa
import com.flexa.core.data.data.AppInfoProvider
import com.flexa.core.data.rest.RestRepository.Companion.json
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.Response

internal abstract class FlexaInterceptor: Interceptor {

    open val headersBundle by lazy(LazyThreadSafetyMode.NONE) {
        val releaseDate = "2025-05-22"
        val appName =
            Flexa.context?.run { AppInfoProvider.getAppName(this) } ?: "Inaccessible"
        val appVersion =
            Flexa.context?.run { AppInfoProvider.getAppVersion(this) } ?: "Inaccessible"
        val appBuildNumber =
            Flexa.context?.run { AppInfoProvider.getAppBuildNumber(this) } ?: "Inaccessible"
        val appPackageName =
            Flexa.context?.run { AppInfoProvider.getAppPackageName(this) } ?: "Inaccessible"
        val sdkVersion = "${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})"
        val userAgent = "Android/$sdkVersion " +
                    "Spend/${BuildConfig.SPEND_SDK_VERSION} " +
                    "$appPackageName/$appVersion ($appBuildNumber)"
        HeadersBundle(
            appName = appName,
            version = releaseDate,
            userAgent = userAgent
        )
    }

    open fun checkResponse(response: Response) {
        Flexa._canSpend.value = runCatching { !isRestricted(response) }.getOrElse { true }
    }

    @Throws(OutOfMemoryError::class, SerializationException::class, IllegalArgumentException::class)
    private fun isRestricted(response: Response): Boolean {
        val raw = response.peekBody(Long.MAX_VALUE).string()
        val jsonResponse = json.parseToJsonElement(raw)
        val res = if ("error" in jsonResponse.jsonObject) {
            val errorObject = jsonResponse.jsonObject["error"]
            val type = errorObject?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
            type == "region_not_supported"
        } else {
            false
        }
        return res
    }
}
