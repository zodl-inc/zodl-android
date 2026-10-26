package co.electriccoin.zcash.ui.screen.migration.setup

import cash.z.ecc.android.sdk.MigrationAvailability
import co.electriccoin.zcash.ui.common.model.migration.MigrationGateInfo
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.design.util.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import co.electriccoin.zcash.ui.design.R as DesignR

class MigrationSetupGateTest {
    private val estimated = Instant.fromEpochSeconds(1_800_000_000L)

    private fun resourceId(resource: StringResource) = (resource as StringResource.ByResource).resource

    @Test
    fun openAvailabilityOffersBothOptions() {
        assertEquals(MigrationSetupGate.Open, MigrationGateInfo.OPEN.toSetupGate())
    }

    @Test
    fun pausedAvailabilityPausesOnlyTheScheduledOption() {
        val gate =
            MigrationGateInfo(MigrationAvailability.PausedUntilNetworkUpgrade(2_000L), estimated).toSetupGate()
        assertIs<MigrationSetupGate.ScheduledPaused>(gate)
        assertEquals(
            DesignR.string.migrationSetup_automaticPausedSubtitle,
            resourceId(gate.automaticSubtitle)
        )
    }

    @Test
    fun pausedAvailabilityWithoutAnEstimateUsesTheCopyWithoutADate() {
        val gate =
            MigrationGateInfo(MigrationAvailability.PausedUntilNetworkUpgrade(2_000L), null).toSetupGate()
        assertIs<MigrationSetupGate.ScheduledPaused>(gate)
        assertEquals(
            DesignR.string.migrationSetup_automaticPausedSubtitleNoDate,
            resourceId(gate.automaticSubtitle)
        )
    }

    @Test
    fun cutoffAvailabilityClosesBothOptionsWithAnExplanation() {
        val gate = MigrationGateInfo(MigrationAvailability.NetworkUpgradeCutoff(2_000L), estimated).toSetupGate()
        assertIs<MigrationSetupGate.Closed>(gate)
        assertEquals(DesignR.string.migrationSetup_cutoffNotice, resourceId(gate.notice))
    }

    @Test
    fun theImmediateOptionIsActedOnWhileTheScheduledOneIsPaused() {
        val paused = MigrationSetupGate.ScheduledPaused(StringResource.ByString("x"))
        assertEquals(MigrationMode.IMMEDIATE, effectiveMigrationMode(MigrationMode.AUTOMATIC, paused))
        assertEquals(MigrationMode.IMMEDIATE, effectiveMigrationMode(MigrationMode.IMMEDIATE, paused))
    }

    @Test
    fun theUserChoiceIsKeptWhenTheGateIsOpen() {
        assertEquals(
            MigrationMode.AUTOMATIC,
            effectiveMigrationMode(MigrationMode.AUTOMATIC, MigrationSetupGate.Open)
        )
        assertEquals(
            MigrationMode.IMMEDIATE,
            effectiveMigrationMode(MigrationMode.IMMEDIATE, MigrationSetupGate.Open)
        )
    }
}
