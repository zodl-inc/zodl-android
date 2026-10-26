package co.electriccoin.zcash.ui.common.model.migration

import cash.z.ecc.android.sdk.MigrationAvailability
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDateTime
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import co.electriccoin.zcash.ui.design.R as DesignR

/**
 * What the NU7 migration gate allows right now, with the estimated wall-clock time of the NU7
 * activation for copy such as "available after the network upgrade (about <date>)".
 *
 * [estimatedActivation] is null when the network has no NU7 height, the gate is open, or the chain
 * tip is not known yet. The gate itself lives in the SDK; the app only reflects it.
 */
data class MigrationGateInfo(
    val availability: MigrationAvailability,
    val estimatedActivation: Instant?,
) {
    /** The scheduled (private, multi-transfer) lane is closed until NU7 activates. */
    val isScheduledLaneAvailable: Boolean get() = availability.isScheduledLaneAvailable

    /** The single-transaction lane stays open before NU7 and closes only in the cutoff window. */
    val isImmediateLaneAvailable: Boolean get() = availability.isImmediateLaneAvailable

    /** No migration lane is open: the last blocks before NU7. */
    val isCutoff: Boolean get() = availability is MigrationAvailability.NetworkUpgradeCutoff

    companion object {
        /** Used when the SDK cannot be asked: the SDK still refuses whatever it must refuse. */
        val OPEN = MigrationGateInfo(MigrationAvailability.Available, estimatedActivation = null)
    }
}

/** The NU7 activation height carried by a closed [MigrationAvailability], or null when it is open. */
fun MigrationAvailability.activationHeightOrNull(): Long? =
    when (this) {
        MigrationAvailability.Available -> null
        is MigrationAvailability.PausedUntilNetworkUpgrade -> activationHeight
        is MigrationAvailability.NetworkUpgradeCutoff -> activationHeight
    }

/**
 * Projects the wall-clock time of block [activationHeight] from [now], given the chain tip. Uses
 * the height-aware rate of [estimatedSecondsBetweenHeights], so a range that ends at NU7 uses the
 * current measured rate up to the activation. Returns null when the tip is unknown (negative).
 */
fun estimateNetworkUpgradeInstant(
    now: Instant,
    tipHeight: Long,
    activationHeight: Long,
    secondsPerBlock: Long,
    nu7ActivationHeight: Long?,
): Instant? =
    if (tipHeight < 0) {
        null
    } else {
        val seconds =
            estimatedSecondsBetweenHeights(
                fromHeight = tipHeight,
                toHeight = activationHeight,
                secondsPerBlock = secondsPerBlock,
                nu7ActivationHeight = nu7ActivationHeight,
            )
        now + seconds.coerceAtLeast(0L).seconds
    }

/**
 * The status line of a run the SDK holds for the NU7 upgrade, with the estimated activation date
 * when the chain tip is known.
 */
fun pausedForNetworkUpgradeRes(estimatedAt: Instant?): StringResource =
    if (estimatedAt == null) {
        stringRes(DesignR.string.migrationHome_pausedSubtitleNoDate)
    } else {
        stringRes(
            DesignR.string.migrationHome_pausedSubtitle,
            stringResByDateTime(
                zonedDateTime =
                    java.time.Instant
                        .ofEpochSecond(estimatedAt.epochSeconds)
                        .atZone(ZoneId.systemDefault()),
                useFullFormat = false,
            ),
        )
    }
