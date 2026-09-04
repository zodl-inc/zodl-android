package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapQuoteMismatchType
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.swap.mismatch.SwapQuoteMismatchArgs
import java.io.IOException
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
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
     * for everything but ZEC. Asserted on the rendered string (via [render] and [strings]), so a copy
     * edit to any of the resources involved shows up here.
     */
    @Test
    fun swapMismatchReportNamesTheModeAndBothAssets() {
        assertEquals(
            "CrossPay - ZEC > USDC (Arbitrum)",
            swapQuoteMismatchSwapTypeLabel(args(mode = SwapMode.EXACT_OUTPUT), ::chainName).render()
        )
    }

    @Test
    fun swapMismatchReportUsesTheModeProductNames() {
        val label = { mode: SwapMode ->
            swapQuoteMismatchSwapTypeLabel(args(mode = mode), ::chainName).render()
        }

        assertTrue(label(SwapMode.EXACT_INPUT).startsWith("Swap - "))
        assertTrue(label(SwapMode.EXACT_OUTPUT).startsWith("CrossPay - "))
        assertTrue(label(SwapMode.FLEX_INPUT).startsWith("Swap into ZEC - "))
    }

    @Test
    fun swapMismatchReportSpellsOutTheChainOfTheSoldAssetToo() {
        assertEquals(
            "Swap into ZEC - USDC (Arbitrum) > ZEC",
            swapQuoteMismatchSwapTypeLabel(
                args(
                    mode = SwapMode.FLEX_INPUT,
                    originTokenTicker = "usdc",
                    originChainTicker = "arb",
                    destinationTokenTicker = "zec",
                    destinationChainTicker = "zec"
                ),
                ::chainName
            ).render()
        )
    }

    @Test
    fun swapMismatchReportNamesTheKnownProvider() {
        assertEquals("NEAR", swapQuoteMismatchProviderLabel("near").render())
    }

    @Test
    fun swapMismatchReportUppercasesAnUnknownProviderId() {
        assertEquals("SOMESWAP", swapQuoteMismatchProviderLabel("someswap").render())
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

    /**
     * The `invoke(args: SwapQuoteMismatchArgs)` overload's message body, pinning all four placeholders
     * of `swap_mismatch_support_email_body`: provider, swap-type line, mismatch type, and deposit
     * address.
     */
    @Test
    fun swapMismatchReportBodyPinsAllFourPlaceholders() {
        assertEquals(
            "Zodl detected the following error in the API response from the swap provider. Providing " +
                "these details allows us to raise it with them to investigate. We won’t relay your " +
                "personal details to the swap provider. You can send this as-is, or include a note to " +
                "us. Thanks in advance.\n\nSwap provider: NEAR\nSwap type: CrossPay - ZEC > USDC " +
                "(Arbitrum)\nMismatch type: Output amount\nQuote ID: deposit-address",
            swapQuoteMismatchReportBody(args(mode = SwapMode.EXACT_OUTPUT), ::chainName).render()
        )
    }

    @Test
    fun swapMismatchReportBodyFallsBackToUnknownQuoteIdWhenDepositAddressIsNull() {
        val body =
            swapQuoteMismatchReportBody(
                args(mode = SwapMode.EXACT_OUTPUT).copy(depositAddress = null),
                ::chainName
            ).render()

        assertTrue(body.endsWith("Quote ID: unknown"))
    }

    private fun chainName(chainTicker: String) =
        stringRes(if (chainTicker == "arb") "Arbitrum" else chainTicker)

    /**
     * The English copy of every resource the mismatch report labels resolve, keyed by resource id --
     * stands in for `Context::getString` so [render] can assert on the exact final strings without an
     * Android context.
     */
    private val strings =
        mapOf(
            R.string.swap_mismatch_mode_swap to "Swap",
            R.string.swap_mismatch_mode_crosspay to "CrossPay",
            R.string.swap_mismatch_mode_swapIntoZec to "Swap into ZEC",
            R.string.swap_mismatch_type_outputAmount to "Output amount",
            R.string.swap_mismatch_type_recipientAddress to "Recipient address",
            R.string.swap_mismatch_provider_near to "NEAR",
            R.string.swap_mismatch_quoteId_unknown to "unknown",
            R.string.swap_mismatch_swapType_format to "%1\$s - %2\$s > %3\$s",
            R.string.swap_mismatch_asset_format to "%1\$s (%2\$s)",
            R.string.swap_mismatch_support_email_body to
                "Zodl detected the following error in the API response from the swap provider. " +
                "Providing these details allows us to raise it with them to investigate. We won’t " +
                "relay your personal details to the swap provider. You can send this as-is, or " +
                "include a note to us. Thanks in advance.\n\nSwap provider: %1\$s\nSwap type: " +
                "%2\$s\nMismatch type: %3\$s\nQuote ID: %4\$s",
        )

    /**
     * Renders a [StringResource] the way `Context::getString` would, resolving [StringResource.ByResource]
     * arguments recursively through [strings] -- so a copy edit to any resource this suite composes shows
     * up as a test failure, not just a resource-id match.
     */
    private fun StringResource.render(): String =
        when (this) {
            is StringResource.ByResource -> {
                String.format(
                    Locale.US,
                    strings.getValue(resource),
                    *args.map { if (it is StringResource) it.render() else it }.toTypedArray()
                )
            }

            is StringResource.ByString -> {
                value
            }

            else -> {
                error("render() only supports the ByResource/ByString shapes this suite composes")
            }
        }

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
