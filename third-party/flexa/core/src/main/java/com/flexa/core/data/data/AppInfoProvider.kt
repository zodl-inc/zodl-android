package com.flexa.core.data.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

internal class AppInfoProvider {
    companion object {

        fun getAppName(application: Context): String =
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
                packageInfo.applicationInfo
                        ?.loadLabel(application.packageManager)
                    ?.toString().orEmpty()
            } catch (e: Exception) {
                ""
            }

        fun getAppVersion(application: Context): String =
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
                packageInfo.versionName ?: "Inaccessible"
            } catch (e: Exception) {
                "Inaccessible"
            }

        fun getAppBuildNumber(application: Context): String =
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toString()
                } else {
                    packageInfo.versionCode.toString()
                }
            } catch (e: Exception) {
                "Inaccessible"
            }

        fun getAppPackageName(context: Context): String =
            try {
                val packageInfo = when {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                        context.packageManager
                            .getPackageInfo(context.packageName, 0)

                    else -> context.packageManager
                        .getPackageInfo(
                            context.packageName,
                            PackageManager.PackageInfoFlags.of(0)
                        )
                }
                packageInfo.applicationInfo?.packageName ?: "Inaccessible"
            } catch (e: Exception) {
                "Inaccessible"
            }
    }
}
