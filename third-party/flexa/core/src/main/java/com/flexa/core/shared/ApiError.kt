package com.flexa.core.shared

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.flexa.BuildConfig
import com.flexa.R
import com.flexa.core.Flexa

sealed class ApiError(
    open val traceId: String? = null,
    open val message: String? = null,
) {

    class InfoEntity(
        val title: String? = null,
        override val message: String? = null,
        override val traceId: String? = null,
    ) : ApiError(traceId = traceId, message = message)

    class ReportEntity(
        val data: String? = null,
        override val message: String? = null,
        override val traceId: String? = null,
    ) : ApiError(traceId = traceId, message = message) {
        val title get() = Flexa.requiredContext.resources.getString(R.string.something_went_wrong)
        val text
            get() = if (!message.isNullOrBlank()) message else
                Flexa.requiredContext.resources.getString(R.string.we_are_sorry_we_encountered_a_problem)

        fun sendEmailReport(activity: Activity) {
            activity.run {
                val errorId = traceId ?: getString(R.string.client_error)

                val messageBody = StringBuilder()
                messageBody.append(getString(R.string.report_email_message_body))
                messageBody.append("\n• Trace id: $errorId")

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
                    val appName =  packageInfo.applicationInfo
                        ?.loadLabel(packageManager)
                        ?.toString().orEmpty()
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
                    Log.e(ReportEntity::class.java.simpleName, ex.message, ex)
                }
            }
        }
    }
}
