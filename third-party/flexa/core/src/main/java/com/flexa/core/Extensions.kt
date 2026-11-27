package com.flexa.core

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.flexa.BuildConfig
import com.flexa.R
import com.flexa.core.data.rest.RestRepository.Companion.json
import com.flexa.core.entity.Account
import com.flexa.core.entity.AssetKey
import com.flexa.core.entity.AvailableAsset
import com.flexa.core.entity.BalanceBundle
import com.flexa.core.entity.ExchangeRate
import com.flexa.core.entity.OneTimeKey
import com.flexa.core.entity.error.ApiException
import com.flexa.core.shared.AssetAccount
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.Response
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

fun String.toNavArgument(): String = try {
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())
} catch (e: java.lang.Exception) {
    ""
}

fun String?.toCurrencySign(): String =
    when (this) {
        "iso4217/USD" -> "\$"
        "iso4217/EUR" -> "€"
        else -> "¤"
    }

fun Instant.minutesBetween(timestamp: Long): Long {
    runCatching {
        Duration.between(this, Instant.ofEpochMilli(timestamp * 1000L))
    }.fold(
        onSuccess = { return it.toMinutes() },
        onFailure = { return 0 }
    )
}

fun List<AssetAccount>.toJsonObject(): JsonObject =
    buildJsonObject {
        putJsonArray("data") {
            forEach { acc ->
                add(acc.toJsonObject())
            }
        }
    }

fun List<com.flexa.core.entity.AppAccount>.getUnitOfAccount(): String? {
    return this.firstOrNull { !it.unitOfAccount.isNullOrBlank() }?.unitOfAccount
}

fun List<com.flexa.core.entity.AppAccount>.getAssetIds(): List<String> {
    return this.flatMap { it.availableAssets }
        .map { it.assetId }.toSet().toList()
}

fun AssetAccount.toJsonObject(): JsonObject =
    buildJsonObject {
        put("account_id", assetAccountHash)
        displayName?.let { put("display_name", it) }
        icon?.let { put("icon", it) }
        if (availableAssets.isNotEmpty()) {
            putJsonArray("assets") {
                availableAssets.forEach { asset ->
                    addJsonObject {
                        put("asset", asset.assetId)
                        put("balance", asset.balance.toString())
                        asset.icon?.let { put("icon", it) }
                        asset.displayName?.let { put("display_name", it) }
                        asset.symbol?.let { put("symbol", it) }
                    }
                }
            }
        }
    }

fun String.toDate(dateFormat: String = "EEE, dd MMM yyyy HH:mm:ss z"): Date =
    try {
        SimpleDateFormat(dateFormat, Locale.US).parse(this) ?: Date()
    } catch (e: ParseException) {
        Log.e(null, "String.toDate:", e)
        Date()
    }

@Throws(SerializationException::class, IllegalArgumentException::class, )
internal fun Response.toApiException(): ApiException {
    val raw = body?.string().toString()
    val traceId = header("client-trace-id", null) ?: ""
    val jsonResponse = json.parseToJsonElement(raw)
    val errorObject = jsonResponse.jsonObject["error"]
    val code =
        errorObject?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
    val message =
        errorObject?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
    val type =
        errorObject?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
    return ApiException(
        code = code, message = message, type = type, traceId = traceId
    )
}

fun AvailableAsset.zeroValue(): Boolean {
    return this.balanceBundle?.total?.let { it == BigDecimal.ZERO } ?: true
}

fun ExchangeRate?.toBalanceBundle(
    asset: com.flexa.core.shared.AvailableAsset?,
): BalanceBundle? {
    return this?.run {
        val scale = 2
        val roundingMode = RoundingMode.DOWN
        val balance = BigDecimal.valueOf(asset?.balance ?: 0.0)
        val balanceAvailable = asset?.balanceAvailable?.run { BigDecimal.valueOf(this) }
        val ratePrice = price?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val total = ratePrice.multiply(balance).setScale(scale, roundingMode)
        val available = balanceAvailable?.run {
            (price?.toBigDecimalOrNull() ?: BigDecimal.ZERO).multiply(this)
                .setScale(scale, roundingMode)
        }
        val currencySign = unitOfAccount?.toCurrencySign()
        val totalLabel = currencySign + total
        val availableLabel = available?.run { currencySign + this }

        BalanceBundle(
            total = total,
            available = available,
            totalLabel = totalLabel,
            availableLabel = availableLabel,
        )
    }
}

fun OneTimeKey.toAssetKey(): AssetKey {
    return AssetKey(
        prefix = this.prefix ?: "",
        secret = this.secret ?: "",
        length = this.length ?: 1
    )
}

fun com.flexa.core.entity.AppAccount.nonZeroAssets(): List<AvailableAsset> =
    availableAssets.filter { !it.zeroValue() }

fun Activity?.sendFlexaReport(data: String? = null) {
    this?.run {
        val messageBody = StringBuilder()
        messageBody.append(getString(R.string.report_email_message_body))
        try {
            val packageInfo = when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                    application.packageManager
                        .getPackageInfo(application.packageName, 0)

                else -> application.packageManager
                    .getPackageInfo(
                        application.packageName,
                        PackageManager.PackageInfoFlags.of(0)
                    )
            }
            val appName = packageInfo.applicationInfo
                ?.loadLabel(packageManager)
                ?.toString().orEmpty().ifBlank { "Unknown App" }
            val appCode = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                packageInfo.versionCode
            } else {
                packageInfo.longVersionCode
            }
            val appVersionName = packageInfo.versionName
            val deviceModel = Build.MODEL
            val deviceManufacturer = Build.MANUFACTURER

            messageBody.append("\n• Application: $appName code: $appCode version name: $appVersionName")
            messageBody.append("\n• Device: $deviceManufacturer $deviceModel Android: ${Build.VERSION.RELEASE} API: ${Build.VERSION.SDK_INT}")
            messageBody.append("\n• Flexa SDK: ${BuildConfig.SPEND_SDK_VERSION} ${BuildConfig.RELEASE_DATE}")
            messageBody.append("\n\n")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        data?.let { messageBody.append(it) }

        val uriText = "mailto:${getString(R.string.flexa_report_email)}" +
                "?subject=" + Uri.encode(getString(R.string.report_an_issue)) +
                "&body=" + Uri.encode(messageBody.toString())
        val uri: Uri = Uri.parse(uriText)
        val sendIntent = Intent(Intent.ACTION_SENDTO)
        sendIntent.data = uri
        try {
            startActivity(Intent.createChooser(sendIntent, getString(R.string.send_email)))
        } catch (ex: ActivityNotFoundException) {
            Log.e(null, ex.message, ex)
        }
    }
}

fun Account.hasLimits(): Boolean {
    return !this.limits.isNullOrEmpty()
}

fun Account.getLimitsPercentage(): Float {
    if (limits.isNullOrEmpty()) {
        return 0F
    } else {
        val limit = limits.firstOrNull()
        val overall = limit?.amount?.toFloatOrNull() ?: 0F
        val remaining = limit?.remaining?.toFloatOrNull() ?: 0F
        return remaining / overall
    }
}

fun Account.getJoinedYear(): String? {
    return if (this.created != null) {
        val timestamp = this.created
        val dateTime =
            LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault())
        val year = dateTime.year
        year.toString()
    } else {
        null
    }
}

fun Account.getResetDay(): String {
    val timestamp = this.limits?.firstOrNull()?.resetsAt ?: Instant.now().toEpochMilli()
    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofPattern("EEEE")
    val formattedDateTime = dateTime.format(formatter)
    return formattedDateTime
}

fun Account.getResetHour(): String {
    val timestamp = this.limits?.firstOrNull()?.resetsAt ?: Instant.now().toEpochMilli()
    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofPattern("h:mm a")
    val formattedDateTime = dateTime.format(formatter)
    return formattedDateTime
}
