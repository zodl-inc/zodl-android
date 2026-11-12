package co.electriccoin.zcash.ui.common.provider

import cash.z.ecc.android.sdk.model.TransactionSizeBound
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** Runs under Robolectric because a missing signer budget is logged via `Twig`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SignerCapabilitiesProviderTest {
    private val zashi = mockk<ZodlAccount>()
    private val keystone = mockk<KeystoneAccount>()

    private var memory = AvailableMemory(availableBytes = 6 * GIB, isLowRamDevice = false)
    private var keystoneBudget: suspend () -> Int = { KEYSTONE_BUDGET }
    private var now = 0L

    private val provider =
        SignerCapabilitiesProviderImpl(
            availableMemorySource = { memory },
            externalSignerBudgetSource = { keystoneBudget() },
            elapsedRealtimeMillis = { now }
        )

    /** Each call starts a new flow, so a test can change the memory between two calls. */
    private suspend fun actionsFor(account: WalletAccount): Int? {
        provider.endFlow()
        return provider.forAccount(account).sizeBound.maxShieldedActions
    }

    /** A call inside the current flow: it reuses the bound measured by the flow's first call. */
    private suspend fun actionsInFlow(account: WalletAccount) =
        provider.forAccount(account).sizeBound.maxShieldedActions

    @Test
    fun zashiBoundFollowsTheMemoryAvailableAfterTheSafetyMargin() =
        runTest {
            memory = memory.copy(availableBytes = 2 * GIB)

            val usable = 2 * GIB * (100 - PROVING_MEMORY_SAFETY_MARGIN_PERCENT) / 100
            assertEquals(
                TransactionSizeBound.forProvingMemory(usable),
                provider.forAccount(zashi).sizeBound
            )
        }

    @Test
    fun theBoundIsMeasuredOncePerFlowAndReusedWhileTheMemoryChanges() =
        runTest {
            memory = memory.copy(availableBytes = 8 * GIB)
            val atMax = actionsInFlow(zashi)

            memory = memory.copy(availableBytes = 1000 * MIB)
            val atReview = actionsInFlow(zashi)

            assertEquals(TransactionSizeBound.MAX_PROVING_ACTIONS, atMax)
            assertEquals(atMax, atReview)
        }

    @Test
    fun aNewFlowMeasuresTheMemoryAgain() =
        runTest {
            memory = memory.copy(availableBytes = 8 * GIB)
            val first = actionsInFlow(zashi)

            provider.endFlow()
            memory = memory.copy(availableBytes = 1000 * MIB)
            val second = actionsInFlow(zashi)

            assertEquals(TransactionSizeBound.MAX_PROVING_ACTIONS, first)
            assertEquals(TransactionSizeBound.MIN_PROVING_ACTIONS, second)
        }

    @Test
    fun aFlowNobodyEndedIsNotTrustedForeverEither() =
        runTest {
            memory = memory.copy(availableBytes = 8 * GIB)
            actionsInFlow(zashi)

            memory = memory.copy(availableBytes = 1000 * MIB)
            now = FLOW_MAX_AGE_MILLIS - 1
            assertEquals(TransactionSizeBound.MAX_PROVING_ACTIONS, actionsInFlow(zashi))
            now = FLOW_MAX_AGE_MILLIS
            assertEquals(TransactionSizeBound.MIN_PROVING_ACTIONS, actionsInFlow(zashi))
        }

    @Test
    fun keystoneAndZashiShareTheFlowsMemoryMeasurement() =
        runTest {
            memory = memory.copy(availableBytes = 8 * GIB)
            keystoneBudget = { 48 }
            actionsInFlow(zashi)

            memory = memory.copy(availableBytes = 1000 * MIB)

            assertEquals(48, actionsInFlow(keystone))
        }

    @Test
    fun softwareSigningIsAlwaysMeasuredFresh() =
        runTest {
            memory = memory.copy(availableBytes = 8 * GIB)
            actionsInFlow(zashi)
            memory = memory.copy(availableBytes = 1000 * MIB)

            assertEquals(
                TransactionSizeBound.MIN_PROVING_ACTIONS,
                provider.forSoftwareSigning().sizeBound.maxShieldedActions
            )
        }

    @Test
    fun aBoundBetweenTheFloorAndTheCapScalesWithTheMemory() =
        runTest {
            // 80 % of 2.5 GiB = 2 GiB: (2048 MiB - 550 MiB) / 20 MiB = 74 actions
            memory = memory.copy(availableBytes = 2560 * MIB)
            val smaller = actionsFor(zashi)
            memory = memory.copy(availableBytes = 2300 * MIB)
            val smallest = actionsFor(zashi)

            assertEquals(74, smaller)
            assertEquals(64, smallest)
        }

    @Test
    fun aLowRamDeviceGetsTheFloorWhateverMemoryItReports() =
        runTest {
            memory = AvailableMemory(availableBytes = 8 * GIB, isLowRamDevice = true)

            assertEquals(TransactionSizeBound.MIN_PROVING_ACTIONS, actionsFor(zashi))
        }

    @Test
    fun noMemoryAtAllStillGetsTheFloor() =
        runTest {
            memory = AvailableMemory(availableBytes = 0, isLowRamDevice = false)

            assertEquals(TransactionSizeBound.MIN_PROVING_ACTIONS, actionsFor(zashi))
        }

    @Test
    fun keystoneIsTheMemoryBoundIntersectedWithTheSigningBudget() =
        runTest {
            memory = memory.copy(availableBytes = 8 * GIB)
            keystoneBudget = { 48 }

            assertEquals(48, actionsFor(keystone))
            assertEquals(TransactionSizeBound.MAX_PROVING_ACTIONS, actionsFor(zashi))
        }

    @Test
    fun keystoneKeepsTheMemoryBoundWhenTheBudgetIsLarger() =
        runTest {
            memory = AvailableMemory(availableBytes = 0, isLowRamDevice = false)

            assertEquals(TransactionSizeBound.MIN_PROVING_ACTIONS, actionsFor(keystone))
        }

    @Test
    fun keystoneBudgetIsTakenFromTheSourceNotFixed() =
        runTest {
            memory = memory.copy(availableBytes = 8 * GIB)
            keystoneBudget = { 30 }
            val tighter = actionsFor(keystone)
            keystoneBudget = { 70 }
            val looser = actionsFor(keystone)

            assertEquals(30, tighter)
            assertEquals(70, looser)
        }

    @Test
    fun keystoneFallsBackToTheMemoryBoundWhenTheBudgetIsUnavailable() =
        runTest {
            memory = memory.copy(availableBytes = 8 * GIB)
            keystoneBudget = { error("no migration sdk") }

            assertEquals(TransactionSizeBound.MAX_PROVING_ACTIONS, actionsFor(keystone))
        }

    @Test
    fun anUnavailableBudgetLeavesExactlyTheMemoryBound() =
        runTest {
            memory = AvailableMemory(availableBytes = 0, isLowRamDevice = false)
            keystoneBudget = { error("unavailable") }

            assertEquals(provider.forAccount(zashi), provider.forAccount(keystone))
        }

    @Test
    fun cancellationWhileReadingTheBudgetIsNotSwallowed() =
        runTest {
            keystoneBudget = { throw CancellationException("cancelled") }

            assertFailsWith<CancellationException> { provider.forAccount(keystone) }
        }

    @Test
    fun softwareSigningIsTheMemoryBoundAndNeverTheSigningBudget() =
        runTest {
            memory = memory.copy(availableBytes = 8 * GIB)
            keystoneBudget = { 1 }

            assertEquals(provider.forAccount(zashi), provider.forSoftwareSigning())
        }

    @Test
    fun noCapabilitySetsASingleChangeOutputYet() =
        runTest {
            assertFalse(provider.forAccount(zashi).singleChangeOutput)
            assertFalse(provider.forAccount(keystone).singleChangeOutput)
        }

    private companion object {
        const val MIB = 1024L * 1024
        const val GIB = 1024L * MIB
        const val KEYSTONE_BUDGET = 96
    }
}
