package co.electriccoin.zcash.ui.screen.support.model

import android.content.Context
import co.electriccoin.zcash.configuration.api.ConfigurationProvider
import co.electriccoin.zcash.spackle.getPackageInfoCompatSuspend
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList

enum class SupportInfoType {
    Time,
    App,
    Os,
    Device,
    Environment,
    Tor,
    Permission,
    Crash
}

data class SupportInfo(
    val timeInfo: TimeInfo,
    val appInfo: AppInfo,
    val configInfo: ConfigInfo,
    val operatingSystemInfo: OperatingSystemInfo,
    val deviceInfo: DeviceInfo,
    val environmentInfo: EnvironmentInfo,
    val torInfo: TorInfo,
    val permissionInfo: PersistentList<PermissionInfo>,
    val crashInfo: PersistentList<CrashInfo>
) {
    // The set of enum values is to allow optional filtering of different types of information
    // by users in the future.  This would mostly be useful for using a web service request to post
    // instead of email (where users can edit the auto generated message)
    fun toSupportString(set: Set<SupportInfoType>) =
        buildString {
            if (set.contains(SupportInfoType.Time)) {
                append(timeInfo.toSupportString())
            }

            if (set.contains(SupportInfoType.App)) {
                append(appInfo.toSupportString())
            }

            if (set.contains(SupportInfoType.Os)) {
                append(operatingSystemInfo.toSupportString())
            }

            if (set.contains(SupportInfoType.Device)) {
                append(deviceInfo.toSupportString())
            }

            if (set.contains(SupportInfoType.Environment)) {
                append(environmentInfo.toSupportString())
            }

            if (set.contains(SupportInfoType.Tor)) {
                append(torInfo.toSupportString())
            }

            if (set.contains(SupportInfoType.Permission)) {
                append(permissionInfo.toPermissionSupportString())
            }

            if (set.contains(SupportInfoType.Crash)) {
                append(crashInfo.toCrashSupportString())
            }
        }

    companion object {
        // Although most of our calls now are non-blocking, we expect more of them to be blocking
        // in the future.
        suspend fun new(
            context: Context,
            androidConfigurationProvider: ConfigurationProvider,
            isTorEnabled: Boolean?
        ): SupportInfo {
            val applicationContext = context.applicationContext
            val packageInfo = applicationContext.packageManager.getPackageInfoCompatSuspend(context.packageName, 0L)

            return SupportInfo(
                TimeInfo.new(packageInfo),
                AppInfo.new(packageInfo, applicationContext),
                ConfigInfo.new(androidConfigurationProvider),
                OperatingSystemInfo.new(),
                DeviceInfo.new(),
                EnvironmentInfo.new(applicationContext),
                TorInfo(isTorEnabled),
                PermissionInfo.all(applicationContext).toPersistentList(),
                CrashInfo.all(context).toPersistentList()
            )
        }
    }
}
