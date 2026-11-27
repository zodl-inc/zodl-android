package co.electriccoin.zcash.ui.common.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.common.model.SharedPayment
import co.electriccoin.zcash.ui.common.model.sharedPayment
import co.electriccoin.zcash.ui.common.usecase.HandleSharedPaymentUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * Holds a payment shared from another app until it has been handled. The Activity hands its intents to [accept] and
 * runs [handlePending] while started, so a share still in flight when the Activity is recreated is retried rather
 * than lost, and a newer share replaces one still in flight.
 */
class SharedPaymentViewModel(
    private val handleSharedPayment: HandleSharedPaymentUseCase
) : ViewModel() {
    private val pending = MutableStateFlow<SharedPayment?>(null)

    /**
     * Takes the share out of [intent]. Like a gift card link opened from outside the app, shared text may carry a
     * spending secret, so it does not stay around in the Activity's intent once read.
     */
    fun accept(intent: Intent) {
        pending.value = intent.sharedPayment() ?: return
        intent.removeExtra(Intent.EXTRA_TEXT)
    }

    suspend fun handlePending() {
        pending.collectLatest { payment ->
            when (payment) {
                is SharedPayment.Text -> handleSharedPayment(payment.text)
                is SharedPayment.Image -> handleSharedPayment(payment.uri)
                null -> return@collectLatest
            }
            pending.compareAndSet(payment, null)
        }
    }
}
