package co.electriccoin.zcash.ui.screen.support.model

import co.electriccoin.zcash.ui.common.provider.IsTorEnabledStorageProvider

data class TorInfo(
    val isTorEnabled: Boolean?
) {
    fun toSupportString() =
        buildString {
            appendLine("Tor enabled: ${isTorEnabled.toSupportValue()}")
        }

    companion object {
        suspend fun new(isTorEnabledStorageProvider: IsTorEnabledStorageProvider) =
            TorInfo(isTorEnabledStorageProvider.get())
    }
}

private fun Boolean?.toSupportValue() =
    when (this) {
        true -> "Yes"
        false -> "No"
        null -> "Not set"
    }
