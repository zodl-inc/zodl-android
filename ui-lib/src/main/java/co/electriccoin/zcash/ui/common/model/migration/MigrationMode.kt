package co.electriccoin.zcash.ui.common.model.migration

import kotlinx.serialization.Serializable

@Serializable
enum class MigrationMode {
    /** Single on-chain transfer sent immediately. Reveals full balance on-chain. */
    IMMEDIATE,

    /** Balance split into multiple transfers sent in background over ~24h. Maximum privacy. */
    AUTOMATIC,
}
