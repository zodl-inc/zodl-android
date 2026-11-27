package co.electriccoin.zcash.ui.common.model.migration

import kotlinx.serialization.Serializable

@Serializable
enum class MigrationTransferStatus { PENDING, SENT }

/**
 * App-side mirror of the engine's per-transaction blocker
 * ([cash.z.ecc.android.sdk.MigrationBlocker]) so the display cache can carry it without
 * depending on the SDK type in a @Serializable model. Never persisted with meaning — it is
 * overwritten from live SDK state on every [withLiveState] overlay.
 */
@Serializable
enum class MigrationTransferBlocker {
    DEPENDENCIES,
    SCHEDULE,
    ANCHOR_BOUNDARY,
    SIGNATURE,
    EXPIRED,
    UNPROVABLE_ANCHOR,
    EXPIRY_IMMINENT,
    AWAITING_REEVALUATION,
    UNSATISFIABLE
}

fun cash.z.ecc.android.sdk.MigrationBlocker.toAppBlocker(): MigrationTransferBlocker =
    when (this) {
        cash.z.ecc.android.sdk.MigrationBlocker.DEPENDENCIES -> MigrationTransferBlocker.DEPENDENCIES
        cash.z.ecc.android.sdk.MigrationBlocker.SCHEDULE -> MigrationTransferBlocker.SCHEDULE
        cash.z.ecc.android.sdk.MigrationBlocker.ANCHOR_BOUNDARY -> MigrationTransferBlocker.ANCHOR_BOUNDARY
        cash.z.ecc.android.sdk.MigrationBlocker.SIGNATURE -> MigrationTransferBlocker.SIGNATURE
        cash.z.ecc.android.sdk.MigrationBlocker.EXPIRED -> MigrationTransferBlocker.EXPIRED
        cash.z.ecc.android.sdk.MigrationBlocker.UNPROVABLE_ANCHOR -> MigrationTransferBlocker.UNPROVABLE_ANCHOR
        cash.z.ecc.android.sdk.MigrationBlocker.EXPIRY_IMMINENT -> MigrationTransferBlocker.EXPIRY_IMMINENT
        cash.z.ecc.android.sdk.MigrationBlocker.AWAITING_REEVALUATION -> MigrationTransferBlocker.AWAITING_REEVALUATION
        cash.z.ecc.android.sdk.MigrationBlocker.UNSATISFIABLE -> MigrationTransferBlocker.UNSATISFIABLE
    }

/**
 * App-side mirror of the engine's actionable next step for a transaction
 * ([cash.z.ecc.android.sdk.MigrationNextAction]) — set only when the transaction is `ready`
 * (mutually exclusive with a [MigrationTransferBlocker]). Drives the "Preparing" / "Sending soon"
 * status copy.
 */
@Serializable
enum class MigrationTransferAction { PROVE, BROADCAST }

fun cash.z.ecc.android.sdk.MigrationNextAction.toAppAction(): MigrationTransferAction =
    when (this) {
        cash.z.ecc.android.sdk.MigrationNextAction.PROVE -> MigrationTransferAction.PROVE
        cash.z.ecc.android.sdk.MigrationNextAction.BROADCAST -> MigrationTransferAction.BROADCAST
    }
