package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.ext.convertZecToZatoshi
import cash.z.ecc.android.sdk.model.Zatoshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.zecdev.zip321.model.PaymentRequest

class PrefillSendUseCase {
    private val bus = Channel<PrefillSendData>()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    operator fun invoke() = bus.receiveAsFlow()

    fun clear() {
        while (bus.tryReceive().isSuccess) {
            // Drain the channel
        }
    }

    fun requestFromTransactionDetail(value: DetailedTransactionData) =
        scope.launch {
            bus.send(
                PrefillSendData.All(
                    amount = value.transaction.amount,
                    address = value.recipient?.address,
                    fee = value.transaction.fee,
                    memos = value.memos
                )
            )
        }

    fun requestFromZip321(value: PaymentRequest) =
        scope.launch {
            val request = value.payments.firstOrNull()
            bus.send(
                PrefillSendData.All(
                    amount =
                        request
                            ?.nonNegativeAmount
                            ?.toZecValueString()
                            ?.toBigDecimal()
                            ?.convertZecToZatoshi() ?: Zatoshi(0),
                    address = request?.recipientAddress?.value,
                    fee = null,
                    memos =
                        value.payments
                            .firstOrNull()
                            ?.memo
                            ?.data
                            ?.decodeToString()
                            ?.let { listOf(it) }
                )
            )
        }

    fun request(value: PrefillSendData) = scope.launch { bus.send(value) }
}

sealed interface PrefillSendData {
    data class All(
        val amount: Zatoshi,
        val address: String?,
        val fee: Zatoshi?,
        val memos: List<String>?,
    ) : PrefillSendData

    data class FromAddressScan(
        val address: String
    ) : PrefillSendData
}
