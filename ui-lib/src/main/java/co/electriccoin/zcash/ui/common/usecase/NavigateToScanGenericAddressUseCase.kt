package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
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
            is ScanAddressPipelineResult.Cancelled -> null
            is ScanAddressPipelineResult.Scanned -> ScanResult(address = result.address, amount = result.amount)
        }
    }

    suspend fun onScanCancelled(args: ScanGenericAddressArgs) {
        pipeline.emit(ScanAddressPipelineResult.Cancelled(args))
        navigationRouter.back()
    }

    suspend fun onScanned(
        address: String,
        amount: BigDecimal?,
        args: ScanGenericAddressArgs
    ) {
        pipeline.emit(ScanAddressPipelineResult.Scanned(address, amount, args))
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
        override val args: ScanGenericAddressArgs
    ) : ScanAddressPipelineResult
}

data class ScanResult(
    val address: String,
    val amount: BigDecimal?
)
