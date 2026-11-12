package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.MemoContent
import cash.z.ecc.android.sdk.model.RecipientAddress
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.Zip317
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.InsufficientFundsException
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmount
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountDataSource
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountFailure
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountUnavailableException
import co.electriccoin.zcash.ui.common.model.ProposalMemoPolicy
import co.electriccoin.zcash.ui.common.provider.SignerCapabilitiesProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import kotlinx.coroutines.CancellationException

/**
 * Where a max amount is sent to.
 */
sealed interface MaxSendTarget {
    /** An external recipient, with the memo text currently typed for it. */
    data class Recipient(
        val address: String,
        val type: AddressType,
        val memoText: String
    ) : MaxSendTarget

    /**
     * The selected account's own transparent receiver (Swap and Pay deposit there). One ZIP-317 marginal fee
     * ([Zip317.marginalFee]) is kept back from the result as a buffer.
     */
    data object OwnTransparentReceiver : MaxSendTarget
}

/**
 * The single access point of the ZIP-317 marginal fee, so tests can replace it and the SDK accessor can change
 * in one place.
 */
fun interface MarginalFeeProvider {
    suspend fun get(): Zatoshi
}

/** Reads the fee from the SDK, which loads it off the main thread on first use and caches it. */
class SdkMarginalFeeProvider : MarginalFeeProvider {
    override suspend fun get(): Zatoshi = Zip317.marginalFee()
}

class GetMaxSendAmountUseCase(
    private val accountDataSource: AccountDataSource,
    private val maxSendAmountDataSource: MaxSendAmountDataSource,
    private val synchronizerProvider: SynchronizerProvider,
    private val marginalFeeProvider: MarginalFeeProvider,
    private val signerCapabilitiesProvider: SignerCapabilitiesProvider,
) {
    /**
     * @throws InsufficientFundsException if nothing is left to send
     * @throws MaxSendAmountUnavailableException if the max amount cannot be determined
     */
    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(target: MaxSendTarget): MaxSendAmount =
        try {
            val account = accountDataSource.getSelectedAccount()
            val recipient = recipientFor(target, account.transparentAddress)
            val memo = memoFor(target)
            maxSendAmountDataSource
                .proposeMaxSendAmount(
                    account = account,
                    recipient = recipient,
                    memo = memo,
                    sizeBound = signerCapabilitiesProvider.forAccount(account).sizeBound
                ).withBuffer(target)
        } catch (e: CancellationException) {
            throw e
        } catch (e: MaxSendAmountUnavailableException) {
            Twig.error { "Max amount unavailable: ${e::class.simpleName} (${e.reason})" }
            throw e
        } catch (e: InsufficientFundsException) {
            Twig.error { "Max amount unavailable: ${e::class.simpleName}" }
            throw e
        } catch (e: Exception) {
            Twig.error { "Max amount unavailable: ${e::class.simpleName}" }
            throw MaxSendAmountUnavailableException(MaxSendAmountFailure.OTHER, e)
        }

    private suspend fun recipientFor(target: MaxSendTarget, ownTransparentAddress: String): RecipientAddress {
        val encoding =
            when (target) {
                is MaxSendTarget.Recipient -> target.address
                MaxSendTarget.OwnTransparentReceiver -> ownTransparentAddress
            }
        return try {
            RecipientAddress.new(encoding, synchronizerProvider.getSynchronizer().network)
        } catch (e: IllegalArgumentException) {
            throw MaxSendAmountUnavailableException(MaxSendAmountFailure.INVALID_RECIPIENT, e)
        }
    }

    private fun memoFor(target: MaxSendTarget): MemoContent? =
        when (target) {
            is MaxSendTarget.Recipient -> {
                ProposalMemoPolicy.memoFor(target.type, target.memoText)?.let { MemoContent.fromString(it) }
            }

            MaxSendTarget.OwnTransparentReceiver -> {
                null
            }
        }

    private suspend fun MaxSendAmount.withBuffer(target: MaxSendTarget): MaxSendAmount {
        val buffer =
            when (target) {
                is MaxSendTarget.Recipient -> Zatoshi(0)
                MaxSendTarget.OwnTransparentReceiver -> marginalFeeProvider.get()
            }
        if (amount.value <= buffer.value) throw InsufficientFundsException()
        return copy(amount = Zatoshi(amount.value - buffer.value))
    }
}
