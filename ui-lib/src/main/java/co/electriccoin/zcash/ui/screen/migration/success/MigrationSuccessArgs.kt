package co.electriccoin.zcash.ui.screen.migration.success

import kotlinx.serialization.Serializable

@Serializable
data class MigrationSuccessArgs(val txId: String? = null)
