package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapQuoteMismatchType
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.swap.mismatch.SwapQuoteMismatchArgs
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * gRPC-failure support email content (MOB-1145): a timeout uses dedicated copy, a non-timeout failure
 * uses its description, and the body only appends a detail paragraph when there is non-blank detail.
 *
 * Also covers MOB-1744: a [SubmitResult.Error] (a failure that happened before any gRPC call was
 * made, e.g. a Sapling parameter download timeout) must be reported as a distinct pre-submission
 * category carrying the real exception type/detail, never as a fake gRPC status.
 *
 * And MOB-1340: the support-facing labels of a rejected-swap-quote report.
 */
class SendEmailUseCaseTest {
    @Test
    fun timeoutReasonUsesTimeoutCopy() {
        assertEquals(
            "timeout-copy",
            grpcFailureReportDescription(
                reason = SubmitResult.GrpcFailure.Reason.TIMEOUT,
                description = "ignored-server-text",
                timeoutCopy = { "timeout-copy" }
            )
        )
    }

    @Test
    fun nullReasonUsesDescriptionAndDoesNotResolveTimeoutCopy() {
        var timeoutCopyResolved = false
        val result =
            grpcFailureReportDescription(
                reason = null,
                description = "server detail",
                timeoutCopy = {
                    timeoutCopyResolved = true
                    "timeout-copy"
                }
            )
        assertEquals("server detail", result)
        assertEquals(false, timeoutCopyResolved)
    }

    @Test
    fun nullReasonWithoutDescriptionIsNull() {
        assertNull(
            grpcFailureReportDescription(
                reason = null,
                description = null,
                timeoutCopy = { "timeout-copy" }
            )
        )
    }

    @Test
    fun bodyWithoutDescriptionIsHeaderOnly() {
        assertEquals("Grpc failure\n", buildGrpcFailureEmailBody(null))
        assertEquals("Grpc failure\n", buildGrpcFailureEmailBody("   "))
    }

    @Test
    fun bodyWithDescriptionAppendsParagraph() {
        assertEquals("Grpc failure\n\nserver detail\n", buildGrpcFailureEmailBody("server detail"))
    }

    /**
     * A genuine transaction-submission failure (real gRPC/mempool status from the SDK) still
     * reports its real code and description -- not the fake `code=-1` used for pre-submission
     * failures (MOB-1744).
     */
    @Test
    fun genuineSubmissionFailureReportsItsRealIndexAndCode() {
        val status =
            submitFailureReportStatus(
                SubmitResult.Failure(
                    txIds = listOf("tx-1"),
                    code = 17,
                    description = "Network unreachable"
                )
            )

        assertEquals(0, status.index)
        assertEquals(false, status.grpcError)
        assertEquals(17, status.code)
        assertEquals("Network unreachable", status.description)
    }

    @Test
    fun genuineSubmissionFailureWithNullDescriptionFallsBackToUnknownError() {
        val status =
            submitFailureReportStatus(
                SubmitResult.Failure(
                    txIds = emptyList(),
                    code = 42,
                    description = null
                )
            )

        assertEquals(42, status.code)
        assertEquals("Unknown error", status.description)
    }

    /**
     * A pre-submission failure -- e.g. an [IOException] thrown while downloading Sapling params,
     * before any gRPC call was made -- is reported with its real exception type and detail, never
     * as a fake gRPC status (MOB-1744).
     */
    @Test
    fun preSubmissionFailureReportsRealExceptionTypeAndDetail() {
        val cause = IOException("Failed to download Sapling parameters")

        val detail = submitErrorPreSubmissionDetail(cause)

        assertEquals("IOException", detail.exceptionType)
        assertTrue(detail.description.contains("Failed to download Sapling parameters"))
    }

    @Test
    fun preSubmissionFailureUsesRealExceptionTypeForDifferentExceptions() {
        val illegalStateDetail = submitErrorPreSubmissionDetail(IllegalStateException("Transaction proposal is null"))

        assertEquals("IllegalStateException", illegalStateDetail.exceptionType)
        assertTrue(illegalStateDetail.description.contains("Transaction proposal is null"))
    }

    /**
     * The swap-type line of a quote-mismatch report follows the design's `CrossPay - ZEC > USDC
     * (Arbitrum)` shape: the mode's product name, then origin > destination with the chain spelled out
     * for everything but ZEC. Asserted structurally on the composed [StringResource], so a copy edit in
     * strings.xml cannot leave this green while changing the email.
     */
    @Test
    fun swapMismatchReportNamesTheModeAndBothAssets() {
        assertEquals(
            stringRes(
                R.string.swap_mismatch_swapType_format,
                stringRes(R.string.swap_mismatch_mode_crosspay),
                stringRes("ZEC"),
                stringRes(R.string.swap_mismatch_asset_format, stringRes("USDC"), stringRes("Arbitrum"))
            ),
            swapQuoteMismatchSwapTypeLabel(args(mode = SwapMode.EXACT_OUTPUT), ::chainName)
        )
    }

    @Test
    fun swapMismatchReportUsesTheModeProductNames() {
        val mode = { mode: SwapMode ->
            swapQuoteMismatchSwapTypeLabel(args(mode = mode), ::chainName)
                .let { assertIs<StringResource.ByResource>(it).args.first() }
        }

        assertEquals(stringRes(R.string.swap_mismatch_mode_swap), mode(SwapMode.EXACT_INPUT))
        assertEquals(stringRes(R.string.swap_mismatch_mode_crosspay), mode(SwapMode.EXACT_OUTPUT))
        assertEquals(stringRes(R.string.swap_mismatch_mode_swapIntoZec), mode(SwapMode.FLEX_INPUT))
    }

    @Test
    fun swapMismatchReportSpellsOutTheChainOfTheSoldAssetToo() {
        assertEquals(
            stringRes(
                R.string.swap_mismatch_swapType_format,
                stringRes(R.string.swap_mismatch_mode_swapIntoZec),
                stringRes(R.string.swap_mismatch_asset_format, stringRes("USDC"), stringRes("Arbitrum")),
                stringRes("ZEC")
            ),
            swapQuoteMismatchSwapTypeLabel(
                args(
                    mode = SwapMode.FLEX_INPUT,
                    originTokenTicker = "usdc",
                    originChainTicker = "arb",
                    destinationTokenTicker = "zec",
                    destinationChainTicker = "zec"
                ),
                ::chainName
            )
        )
    }

    @Test
    fun swapMismatchReportNamesTheKnownProvider() {
        assertEquals(stringRes(R.string.swap_mismatch_provider_near), swapQuoteMismatchProviderLabel("near"))
    }

    @Test
    fun swapMismatchReportUppercasesAnUnknownProviderId() {
        assertEquals(stringRes("SOMESWAP"), swapQuoteMismatchProviderLabel("someswap"))
    }

    @Test
    fun swapMismatchTypesCarryASupportFacingLabel() {
        assertEquals(
            R.string.swap_mismatch_type_outputAmount,
            SwapQuoteMismatchType.OUTPUT_AMOUNT.reportLabelRes
        )
        assertEquals(
            R.string.swap_mismatch_type_recipientAddress,
            SwapQuoteMismatchType.RECIPIENT_ADDRESS.reportLabelRes
        )
    }

    private fun chainName(chainTicker: String) =
        stringRes(if (chainTicker == "arb") "Arbitrum" else chainTicker)

    @Suppress("LongParameterList")
    private fun args(
        mode: SwapMode,
        originTokenTicker: String = "zec",
        originChainTicker: String = "zec",
        destinationTokenTicker: String = "usdc",
        destinationChainTicker: String = "arb"
    ) = SwapQuoteMismatchArgs(
        provider = "near",
        mode = mode,
        originTokenTicker = originTokenTicker,
        originChainTicker = originChainTicker,
        destinationTokenTicker = destinationTokenTicker,
        destinationChainTicker = destinationChainTicker,
        mismatchType = SwapQuoteMismatchType.OUTPUT_AMOUNT,
        depositAddress = "deposit-address"
    )
}
