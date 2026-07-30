package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.exception.CompactBlockProcessorException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Recognising the failures where ZODL and the lightwalletd server disagree about the state of the
 * network. These are read off the SDK exception's properties rather than parsed out of its message,
 * whose wording is not a contract.
 */
class ServerCompatibilityErrorTest {
    @Test
    fun readsBothBranchIdsFromAConsensusBranchMismatch() {
        val error =
            SynchronizerError.Processor(
                CompactBlockProcessorException.MismatchedConsensusBranch(
                    clientBranchId = CLIENT_BRANCH_ID,
                    serverBranchId = SERVER_BRANCH_ID
                )
            )

        assertEquals(
            ServerCompatibilityError.ConsensusBranch(
                clientBranchId = CLIENT_BRANCH_ID,
                serverBranchId = SERVER_BRANCH_ID
            ),
            error.toServerCompatibilityError()
        )
    }

    @Test
    fun readsBothSidesOfANetworkMismatch() {
        val error =
            SynchronizerError.Processor(
                CompactBlockProcessorException.MismatchedNetwork(
                    clientNetwork = "mainnet",
                    serverNetwork = "testnet"
                )
            )

        assertEquals(
            ServerCompatibilityError.Network(clientNetwork = "mainnet", serverNetwork = "testnet"),
            error.toServerCompatibilityError()
        )
    }

    @Test
    fun readsBothSidesOfASaplingActivationHeightMismatch() {
        val error =
            SynchronizerError.Processor(
                CompactBlockProcessorException.MismatchedSaplingActivationHeight(
                    clientHeight = 419_200L,
                    serverHeight = 280_000L
                )
            )

        assertEquals(
            ServerCompatibilityError.SaplingActivationHeight(clientHeight = 419_200L, serverHeight = 280_000L),
            error.toServerCompatibilityError()
        )
    }

    @Test
    fun findsTheMismatchWhenTheSdkHasWrappedIt() {
        val wrapped =
            IllegalStateException(
                "sync failed",
                CompactBlockProcessorException.MismatchedConsensusBranch(
                    clientBranchId = CLIENT_BRANCH_ID,
                    serverBranchId = SERVER_BRANCH_ID
                )
            )

        val result = SynchronizerError.Processor(RuntimeException("outer", wrapped)).toServerCompatibilityError()

        assertEquals(
            ServerCompatibilityError.ConsensusBranch(
                clientBranchId = CLIENT_BRANCH_ID,
                serverBranchId = SERVER_BRANCH_ID
            ),
            result
        )
    }

    @Test
    fun ignoresErrorsThatAreNotCompatibilityFailures() {
        assertNull(SynchronizerError.Processor(IOException("connection reset")).toServerCompatibilityError())
    }

    @Test
    fun ignoresErrorsWithNoCauseAtAll() {
        assertNull(SynchronizerError.Processor(null).toServerCompatibilityError())
    }

    @Test
    fun namesTheUnderlyingSdkExceptionAsTheErrorType() {
        assertEquals(
            "MismatchedConsensusBranch",
            ServerCompatibilityError.ConsensusBranch(CLIENT_BRANCH_ID, SERVER_BRANCH_ID).type
        )
        assertEquals("MismatchedNetwork", ServerCompatibilityError.Network("mainnet", "testnet").type)
        assertEquals(
            "MismatchedSaplingActivationHeight",
            ServerCompatibilityError.SaplingActivationHeight(1L, 2L).type
        )
    }
}

// NU6.2, which the SDK expects, and NU6.3/Ironwood, observed on a server ahead of the app.
private const val CLIENT_BRANCH_ID = "5437f330"
private const val SERVER_BRANCH_ID = "37a5165b"
