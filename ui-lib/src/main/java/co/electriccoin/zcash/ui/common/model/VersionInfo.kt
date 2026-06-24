package co.electriccoin.zcash.ui.common.model

import android.content.Context
import android.content.pm.ApplicationInfo
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.build.gitCommitCount
import co.electriccoin.zcash.build.gitSha
import co.electriccoin.zcash.build.releaseNotesEn
import co.electriccoin.zcash.build.releaseNotesEs
import co.electriccoin.zcash.build.releaseNotesPt
import co.electriccoin.zcash.spackle.FirebaseTestLabUtil
import co.electriccoin.zcash.spackle.getPackageInfoCompat
import co.electriccoin.zcash.spackle.versionCodeCompat
import co.electriccoin.zcash.ui.BuildConfig
import co.electriccoin.zcash.ui.design.util.getPreferredLocale

data class VersionInfo(
    val gitSha: String,
    val gitCommitCount: Long,
    val changelog: Changelog,
    val isDebuggable: Boolean,
    val isRunningUnderTestService: Boolean,
    val versionCode: Long,
    val versionName: String,
    val distribution: DistributionDimension,
    val network: ZcashNetwork,
) {
    companion object {
        val NETWORK_DIMENSION: NetworkDimension
            get() = NetworkDimension.entries.first { it.value == BuildConfig.FLAVOR_network }

        val DISTRIBUTION: DistributionDimension
            get() = DistributionDimension.entries.first { it.value == BuildConfig.FLAVOR_distribution }
        val NETWORK: ZcashNetwork
            get() =
                when (NETWORK_DIMENSION) {
                    NetworkDimension.MAINNET -> ZcashNetwork.Mainnet
                    NetworkDimension.TESTNET -> ZcashNetwork.Testnet
                }

        val IS_CMC_AVAILABLE = BuildConfig.ZCASH_CMC_KEY.takeIf { it.isNotBlank() } != null

        fun new(context: Context): VersionInfo {
            val packageInfo = context.packageManager.getPackageInfoCompat(context.packageName, 0L)
            val applicationInfo = context.applicationInfo

            return VersionInfo(
                // Should only be null during tests
                versionName = packageInfo.versionName?.let { "$it (${packageInfo.versionCodeCompat})" } ?: "null",
                // Should only be 0 during tests
                versionCode = packageInfo.versionCodeCompat,
                isDebuggable = (0 != applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE),
                isRunningUnderTestService = FirebaseTestLabUtil.isFirebaseTestLab(context.applicationContext),
                gitSha = gitSha,
                gitCommitCount = gitCommitCount.toLong(),
                changelog = Changelog.new(json = resolveBestReleaseNotes(context)),
                distribution = DISTRIBUTION,
                network = NETWORK
            )
        }

        private fun resolveBestReleaseNotes(context: Context): String {
            // Get the locale from the context configuration and ensure it's a supported locale
            val locale = context.resources.configuration.getPreferredLocale()
            return when {
                locale.language.contains("es", ignoreCase = true) -> releaseNotesEs
                locale.language.contains("pt", ignoreCase = true) -> releaseNotesPt
                else -> releaseNotesEn
            }
        }
    }
}

enum class DistributionDimension(
    val value: String
) {
    STORE("store"),
    FOSS("foss"),
    INTERNAL("internal")
}

enum class NetworkDimension(
    val value: String
) {
    MAINNET("zcashmainnet"),
    TESTNET("zcashtestnet")
}
