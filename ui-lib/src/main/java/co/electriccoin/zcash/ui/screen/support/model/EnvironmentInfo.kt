package co.electriccoin.zcash.ui.screen.support.model

import android.content.Context
import cash.z.ecc.android.sdk.model.MonetarySeparators
import co.electriccoin.zcash.global.StorageChecker
import co.electriccoin.zcash.ui.design.util.getPreferredLocale
import java.text.DecimalFormatSymbols
import java.util.Locale

// TODO [#1301]: Localize support text content
// TODO [#1301]: https://github.com/Electric-Coin-Company/zashi-android/issues/1301

data class EnvironmentInfo(
    val locale: Locale,
    val monetarySeparators: MonetarySeparators,
    val usableStorageMegabytes: Int
) {
    fun toSupportString() =
        buildString {
            appendLine("Locale: ${locale.androidResName()}")
            appendLine("Currency grouping separator: ${monetarySeparators.grouping}")
            appendLine("Currency decimal separator: ${monetarySeparators.decimal}")
            appendLine("Usable storage: $usableStorageMegabytes MB")
        }

    companion object {
        suspend fun new(context: Context): EnvironmentInfo {
            val usableStorage = StorageChecker.checkAvailableStorageMegabytes()
            val locale = context.resources.configuration.getPreferredLocale()
            val symbols = DecimalFormatSymbols(locale)
            val separators =
                MonetarySeparators(
                    grouping = symbols.groupingSeparator,
                    decimal = symbols.decimalSeparator
                )

            return EnvironmentInfo(
                context.resources.configuration.locales[0],
                // This MonetarySeparators calling reflects the real separators according to device Locale as its
                // goal is to represent information about the device, and it's not considered to be used in UI
                separators,
                usableStorage
            )
        }
    }
}

private fun Locale.androidResName() = "$language-$country"
