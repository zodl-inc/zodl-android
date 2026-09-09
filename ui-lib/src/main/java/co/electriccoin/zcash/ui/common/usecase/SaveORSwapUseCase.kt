package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.SwapStatus
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import co.electriccoin.zcash.ui.common.repository.SwapQuoteData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import kotlinx.coroutines.flow.first
import java.math.BigDecimal

class SaveORSwapUseCase(
    private val swapRepository: SwapRepository,
    private val metadataRepository: MetadataRepository,
    private val navigationRouter: NavigationRouter,
    // private val ephemeralAddressRepository: EphemeralAddressRepository,
) {
    suspend operator fun invoke() {
        val quote = (swapRepository.quote.first() as? SwapQuoteData.Success)?.quote
        if (quote != null) {
            metadataRepository.markTxAsSwap(
                depositAddress = quote.depositAddress.address,
                provider = quote.provider.value,
                totalFees = Zatoshi(0),
                totalFeesUsd = BigDecimal(0),
                amountOutFormatted = quote.amountOutFormatted,
                mode = quote.mode,
                status = SwapStatus.PENDING,
                origin = quote.originAsset,
                destination = quote.destinationAsset
            )
            // ephemeralAddressRepository.invalidate()
            swapRepository.clear()
            navigationRouter.backToRoot()
        }
    }
}
