package co.electriccoin.zcash.ui.common.model.migration

import cash.z.ecc.android.sdk.MigrationAvailability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class MigrationGateInfoTest {
    private val now = Instant.fromEpochSeconds(1_000_000L)

    @Test
    fun activationHeightIsOnlyPresentWhileTheGateIsClosed() {
        assertNull(MigrationAvailability.Available.activationHeightOrNull())
        assertEquals(500L, MigrationAvailability.PausedUntilNetworkUpgrade(500L).activationHeightOrNull())
    }

    @Test
    fun lanesFollowTheAvailability() {
        val open = MigrationGateInfo.OPEN
        assertTrue(open.isScheduledLaneAvailable)
        assertTrue(open.isImmediateLaneAvailable)

        val paused = MigrationGateInfo(MigrationAvailability.PausedUntilNetworkUpgrade(500L), now)
        assertFalse(paused.isScheduledLaneAvailable)
        assertTrue(paused.isImmediateLaneAvailable)
    }

    @Test
    fun estimateUsesTheMeasuredRateBeforeTheActivation() {
        // 99 blocks below the activation at 75 s, plus the activation block itself at the 25 s NU7 spacing.
        assertEquals(
            now + 7_450L.toDuration(),
            estimateNetworkUpgradeInstant(
                now = now,
                tipHeight = 900L,
                activationHeight = 1_000L,
                secondsPerBlock = 75L,
                nu7ActivationHeight = 1_000L,
            ),
        )
    }

    @Test
    fun estimateIsNowWhenTheActivationIsAlreadyReached() {
        assertEquals(
            now,
            estimateNetworkUpgradeInstant(
                now = now,
                tipHeight = 1_200L,
                activationHeight = 1_000L,
                secondsPerBlock = 75L,
                nu7ActivationHeight = 1_000L,
            ),
        )
    }

    @Test
    fun estimateIsUnknownWithoutAChainTip() {
        assertNull(
            estimateNetworkUpgradeInstant(
                now = now,
                tipHeight = -1L,
                activationHeight = 1_000L,
                secondsPerBlock = 75L,
                nu7ActivationHeight = 1_000L,
            )
        )
    }

    private fun Long.toDuration() = kotlin.time.Duration.parse("${this}s")
}
