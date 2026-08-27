package co.electriccoin.zcash.ui.common.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class PrepareVotingRoundUseCaseTest {
    @Test
    fun preparedRecoveryResumesWithoutReadingNativeBundleCount() =
        runTest {
            var bundleCountReads = 0

            val action =
                existingRoundRecoveryAction(
                    hasPreparedRecovery = true,
                    getBundleCount = {
                        bundleCountReads += 1
                        0
                    }
                )

            assertEquals(ExistingRoundRecoveryAction.RESUME, action)
            assertEquals(0, bundleCountReads)
        }

    @Test
    fun nativeBundlesResumeWhenPreferenceRecoveryIsMissing() =
        runTest {
            val action =
                existingRoundRecoveryAction(
                    hasPreparedRecovery = false,
                    getBundleCount = { 1 }
                )

            assertEquals(ExistingRoundRecoveryAction.RESUME, action)
        }

    @Test
    fun emptyExistingRoundIsSafeToReinitialize() =
        runTest {
            val action =
                existingRoundRecoveryAction(
                    hasPreparedRecovery = false,
                    getBundleCount = { 0 }
                )

            assertEquals(ExistingRoundRecoveryAction.REINITIALIZE, action)
        }

    @Test
    fun unknownNativeStateFailsClosedInsteadOfRequestingClear() =
        runTest {
            val action =
                existingRoundRecoveryAction(
                    hasPreparedRecovery = false,
                    getBundleCount = { error("database unavailable") }
                )

            assertEquals(ExistingRoundRecoveryAction.FAIL_CLOSED, action)
        }

    @Test
    fun nativeBundleLookupCancellationPropagates() =
        runTest {
            val cancellation = CancellationException("cancelled")

            val thrown =
                assertFailsWith<CancellationException> {
                    existingRoundRecoveryAction(
                        hasPreparedRecovery = false,
                        getBundleCount = { throw cancellation }
                    )
                }

            assertSame(cancellation, thrown)
        }
}
