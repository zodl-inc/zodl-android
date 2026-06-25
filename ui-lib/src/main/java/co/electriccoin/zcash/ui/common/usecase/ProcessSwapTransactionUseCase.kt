package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.datasource.SwapDataSource
import co.electriccoin.zcash.ui.common.datasource.SwapTransactionProposal
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.common.model.SwapStatus
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import kotlinx.coroutines.yield

class ProcessSwapTransactionUseCase(
    private val metadataRepository: MetadataRepository,
    private val swapDataSource: SwapDataSource,
    // private val ephemeralAddressRepository: EphemeralAddressRepository,
) {
    suspend operator fun invoke(transactionProposal: SwapTransactionProposal, result: SubmitResult) {
        saveSwapToMetadata(transactionProposal)
        // invalidateEphemeralAddress(result)
        submitDepositTransactions(transactionProposal, result)
    }

    // private suspend fun invalidateEphemeralAddress(result: SubmitResult) {
    //     when (result) {
    //         is SubmitResult.Failure,
    //         is SubmitResult.GrpcFailure,
    //         is SubmitResult.Success -> ephemeralAddressRepository.invalidate()
    //
    //         is SubmitResult.Partial -> {
    //             // do nothing
    //         }
    //     }
    // }

    private fun saveSwapToMetadata(transactionProposal: SwapTransactionProposal) {
        metadataRepository.markTxAsSwap(
            depositAddress = transactionProposal.destination.address,
            provider = transactionProposal.quote.provider,
            totalFees = transactionProposal.totalFees,
            totalFeesUsd = transactionProposal.totalFeesUsd,
            amountOutFormatted = transactionProposal.quote.amountOutFormatted,
            origin = transactionProposal.quote.originAsset,
            destination = transactionProposal.quote.destinationAsset,
            mode = transactionProposal.quote.mode,
            status = SwapStatus.PENDING
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun submitDepositTransactions(transactionProposal: SwapTransactionProposal, result: SubmitResult) {
        suspend fun submit(txId: String, transactionProposal: SwapTransactionProposal) {
            try {
                swapDataSource.submitDepositTransaction(
                    txHash = txId,
                    depositAddress = transactionProposal.destination.address
                )
            } catch (e: Exception) {
                Twig.error(e) { "Unable to submit deposit transaction" }
            }
        }

        result.txIds
            .filter { it.isNotEmpty() }
            .forEach {
                submit(it, transactionProposal)
                yield()
            }
    }
}
