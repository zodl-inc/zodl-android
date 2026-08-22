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
    val isPaymentRequest: Boolean
)

fun ScanResult.resolve(assets: Collection<SwapAsset>, currentAsset: SwapAsset?): ResolvedScanResult {
    val requestedAsset = request?.resolveAsset(assets, currentAsset)
    return ResolvedScanResult(
        address = address,
        amount = request?.resolvedAmount(requestedAsset) ?: amount.takeIf { request == null },
        asset = requestedAsset ?: currentAsset.takeIf { request == null },
        isPaymentRequest = request != null
    )
}
