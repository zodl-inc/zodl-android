package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.MigrationAvailability
import cash.z.ecc.android.sdk.MigrationWalletTipUnavailableException
import cash.z.ecc.android.sdk.OrchardMigrationSdk
import co.electriccoin.zcash.ui.common.model.migration.MigrationGateInfo
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GetMigrationGateInfoUseCaseTest {
    private val activation = 4_465_026L

    private fun useCase(sdk: OrchardMigrationSdk) =
        GetMigrationGateInfoUseCase(getOrchardMigrationSdk = mockk { coEvery { this@mockk.invoke() } returns sdk })

    @Test
    fun a_wallet_without_a_chain_tip_gets_a_closed_scheduled_lane_and_no_date() =
        runBlocking {
            val sdk =
                mockk<OrchardMigrationSdk> {
                    coEvery { migrationAvailability() } throws MigrationWalletTipUnavailableException()
                    coEvery { nu7ActivationHeight } returns activation
                }

            val info = useCase(sdk)()

            assertEquals(MigrationAvailability.PausedUntilNetworkUpgrade(activation), info.availability)
            assertEquals(null, info.estimatedActivation)
            assertFalse(info.isScheduledLaneAvailable)
            assertEquals(true, info.isImmediateLaneAvailable)
        }

    @Test
    fun any_other_failure_leaves_the_lane_open_and_the_sdk_decides() =
        runBlocking {
            val sdk =
                mockk<OrchardMigrationSdk> {
                    coEvery { migrationAvailability() } throws IllegalStateException("db locked")
                }

            assertEquals(MigrationGateInfo.OPEN, useCase(sdk)())
        }
}
