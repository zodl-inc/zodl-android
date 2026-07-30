package co.electriccoin.zcash.ui.screen.error

import cash.z.ecc.android.sdk.exception.CompactBlockProcessorException
import co.electriccoin.lightwallet.client.model.ResponseException
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.SynchronizerError
import co.electriccoin.zcash.ui.common.repository.HomeMessageData
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which sync failures reach the Sync Error sheet. The sheet is the only place that offers "Switch
 * server", so a failure whose remedy is changing server has to route here rather than to the
 * generic error sheet, which shows a stack trace and no way to change server.
 */
class NavigateToErrorUseCaseTest {
    @Test
    fun consensusBranchMismatchOpensTheSyncErrorSheet() {
        val router = mockk<NavigationRouter>(relaxed = true)

        val handled =
            NavigateToErrorUseCase(router).navigateToSyncError(
                error(
                    CompactBlockProcessorException.MismatchedConsensusBranch(
                        clientBranchId = "5437f330",
                        serverBranchId = "37a5165b"
                    )
                )
            )

        assertTrue(handled)
        verify(exactly = 1) { router.forward(SyncErrorArgs) }
    }

    @Test
    fun networkMismatchOpensTheSyncErrorSheet() {
        val router = mockk<NavigationRouter>(relaxed = true)

        val handled =
            NavigateToErrorUseCase(router).navigateToSyncError(
                error(CompactBlockProcessorException.MismatchedNetwork("mainnet", "testnet"))
            )

        assertTrue(handled)
        verify(exactly = 1) { router.forward(SyncErrorArgs) }
    }

    @Test
    fun saplingActivationHeightMismatchOpensTheSyncErrorSheet() {
        val router = mockk<NavigationRouter>(relaxed = true)

        val handled =
            NavigateToErrorUseCase(router).navigateToSyncError(
                error(CompactBlockProcessorException.MismatchedSaplingActivationHeight(419_200L, 280_000L))
            )

        assertTrue(handled)
        verify(exactly = 1) { router.forward(SyncErrorArgs) }
    }

    @Test
    fun serverErrorsStillOpenTheSyncErrorSheet() {
        val router = mockk<NavigationRouter>(relaxed = true)

        val handled =
            NavigateToErrorUseCase(router).navigateToSyncError(
                error(ResponseException(503, "unavailable", IOException("transport")))
            )

        assertTrue(handled)
        verify(exactly = 1) { router.forward(SyncErrorArgs) }
    }

    @Test
    fun unrelatedFailuresAreLeftToTheGenericErrorHandling() {
        val router = mockk<NavigationRouter>(relaxed = true)

        val handled = NavigateToErrorUseCase(router).navigateToSyncError(error(IOException("boom")))

        assertFalse(handled)
        verify(exactly = 0) { router.forward(any()) }
    }
}

private fun error(cause: Throwable) = HomeMessageData.Error(SynchronizerError.Processor(cause))
