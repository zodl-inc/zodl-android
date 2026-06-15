package co.electriccoin.zcash.ui.common.model.migration

import kotlinx.serialization.Serializable

@Serializable
enum class MigrationTransferStatus { PENDING, SENT, FAILED }
