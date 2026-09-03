package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.CrossPayRequest
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.screen.scan.ScanGenericAddressArgs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import java.math.BigDecimal

class NavigateToScanGenericAddressUseCase(
    private val navigationRouter: NavigationRouter
) {
    private val pipeline = MutableSharedFlow<ScanAddressPipelineResult>()

    suspend operator fun invoke(): ScanResult? {
        val args = ScanGenericAddressArgs()
        navigationRouter.forward(args)
        val result = pipeline.first { it.args.requestId == args.requestId }
        return when (result) {
            is ScanAddressPipelineResult.Cancelled -> {
                null
            }

            is ScanAddressPipelineResult.Scanned -> {
                ScanResult(address = result.address, amount = result.amount, request = result.request)
            }
        }
    }

    suspend fun onScanCancelled(args: ScanGenericAddressArgs) {
        pipeline.emit(ScanAddressPipelineResult.Cancelled(args))
        navigationRouter.back()
    }

    suspend fun onScanned(
        address: String,
        amount: BigDecimal?,
        args: ScanGenericAddressArgs,
        request: CrossPayRequest? = null
    ) {
        pipeline.emit(ScanAddressPipelineResult.Scanned(address, amount, request, args))
    }
}

private sealed interface ScanAddressPipelineResult {
    val args: ScanGenericAddressArgs

    data class Cancelled(
        override val args: ScanGenericAddressArgs
    ) : ScanAddressPipelineResult

    data class Scanned(
        val address: String,
        val amount: BigDecimal?,
        val request: CrossPayRequest?,
        override val args: ScanGenericAddressArgs
    ) : ScanAddressPipelineResult
}

data class ScanResult(
    val address: String,
    val amount: BigDecimal?,
    val request: CrossPayRequest? = null
)

data class ResolvedScanResult(
    val address: String,
    val amount: BigDecimal?,
    val asset: SwapAsset?,
    // True only when `request` was both recognized AND resolved to a concrete asset -- not merely
    // whenever a payment-request URI was recognized. Kept false for an unresolved-but-recognized
    // request (unsupported chain, assets not yet loaded, ambiguous match) so callers fall through
    // to their "leave existing state alone" branch instead of clearing a good selection/amount for
    // a request they can't actually act on.
    val isResolvedPaymentRequest: Boolean
)

fun ScanResult.resolve(assets: Collection<SwapAsset>, currentAsset: SwapAsset?): ResolvedScanResult {
    val requestedAsset = request?.resolveAsset(assets, currentAsset)
    val isResolvedPaymentRequest = request != null && requestedAsset != null
    return ResolvedScanResult(
        address = address,
        amount =
            when {
                request != null && requestedAsset != null -> request.resolvedAmount(requestedAsset)
                request == null -> amount
                else -> null
            },
        // Falls back to currentAsset whenever nothing concrete was resolved, regardless of
        // whether a request was recognized at all -- an unresolvable request must not wipe out a
        // perfectly good existing selection (see isResolvedPaymentRequest doc above).
        asset = requestedAsset ?: currentAsset,
        isResolvedPaymentRequest = isResolvedPaymentRequest
    )
}
