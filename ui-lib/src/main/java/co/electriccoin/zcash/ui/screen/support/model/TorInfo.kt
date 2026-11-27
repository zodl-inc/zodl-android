package co.electriccoin.zcash.ui.screen.support.model

data class TorInfo(
    val isTorEnabled: Boolean?
) {
    fun toSupportString() =
        buildString {
            appendLine("Tor enabled: ${isTorEnabled.toSupportValue()}")
        }
}

private fun Boolean?.toSupportValue() =
    when (this) {
        true -> "Yes"
        false -> "No"
        null -> "Not set"
    }
