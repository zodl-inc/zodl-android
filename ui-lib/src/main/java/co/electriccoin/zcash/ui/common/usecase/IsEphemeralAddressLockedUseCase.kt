package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.repository.EphemeralAddressRepository
import co.electriccoin.zcash.ui.common.repository.SendTransaction
import co.electriccoin.zcash.ui.common.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.lang.IllegalStateException

class IsEphemeralAddressLockedUseCase(
    private val transactionRepository: TransactionRepository,
    private val ephemeralAddressRepository: EphemeralAddressRepository
) {
    @Suppress("MagicNumber", "UseCheckOrError")
    suspend operator fun invoke(): EphemeralLockState {
        val ephemeral =
            ephemeralAddressRepository.get()
                ?: throw IllegalStateException("Ephemeral address is null")

        val transactions = transactionRepository.getTransactions()

        return if (ephemeral.gapLimit - ephemeral.gapPosition > 1u) {
            EphemeralLockState.UNLOCKED
        } else {
            val unlockTransaction =
                transactions
                    .filterIsInstance<SendTransaction>()
                    .find {
                        val fee = it.fee ?: Zatoshi(0)
                        val amount = it.amount - fee
                        it.recipient == ephemeral.address && amount <= Zatoshi(100)
                    }

            when (unlockTransaction) {
                is SendTransaction.Failed -> EphemeralLockState.LOCKED
                is SendTransaction.Pending -> EphemeralLockState.UNLOCKING
                is SendTransaction.Success -> EphemeralLockState.UNLOCKED
                null -> EphemeralLockState.LOCKED
            }
        }
    }

    @Suppress("MagicNumber")
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe() =
        ephemeralAddressRepository
            .observe()
            .filterNotNull()
            .flatMapLatest { ephemeral ->
                if (ephemeral.gapLimit - ephemeral.gapPosition > 1u) {
                    flowOf(false)
                } else {
                    transactionRepository
                        .transactions
                        .filterNotNull()
                        .map { transactions ->
                            transactions
                                .filterIsInstance<SendTransaction.Success>()
                                .none {
                                    val fee = it.fee ?: Zatoshi(0)
                                    val amount = it.amount - fee
                                    it.recipient == ephemeral.address && amount <= Zatoshi(100)
                                }
                        }
                }
            }
}

enum class EphemeralLockState {
    LOCKED,
    UNLOCKING,
    UNLOCKED
}
