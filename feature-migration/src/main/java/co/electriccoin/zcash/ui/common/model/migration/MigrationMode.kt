package co.electriccoin.zcash.ui.common.model.migration

// No @Serializable annotation: kotlinx.serialization auto-generates an enum serializer when this
// enum is used as a field of another @Serializable type (e.g. the Migration*Args nav routes), which
// is the only way it is ever serialized here.
enum class MigrationMode {
    /** Single on-chain transfer sent immediately. Reveals full balance on-chain. */
    IMMEDIATE,

    /** Balance split into multiple transfers sent in background over ~24h. Maximum privacy. */
    AUTOMATIC,
}
