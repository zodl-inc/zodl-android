package co.electriccoin.zcash.ui.common.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.IncomingPaymentIntent
import co.electriccoin.zcash.ui.common.model.incomingPayment
import co.electriccoin.zcash.ui.common.usecase.HandleSharedPaymentUseCase
import co.electriccoin.zcash.ui.screen.scan.thirdparty.ThirdPartyScan
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest

class IncomingPaymentViewModel(
    private val handleSharedPayment: HandleSharedPaymentUseCase,
    private val navigationRouter: NavigationRouter
) : ViewModel() {
    private val pending = MutableStateFlow<Request?>(null)

    fun accept(intent: Intent) {
        val content = intent.incomingPayment() ?: return
        pending.value = Request(content)
    }

    // The Activity collects while STARTED. Cancellation leaves unfinished work pending in this
    // retained ViewModel, so a replacement Activity retries it instead of losing the share.
    suspend fun processPending() {
        pending.collectLatest { request ->
            when (val content = request?.content) {
                is IncomingPaymentIntent.Image -> handleSharedPayment(content.uri)
                is IncomingPaymentIntent.Text -> handleSharedPayment(content.text)
                IncomingPaymentIntent.ThirdPartyView -> navigationRouter.forward(ThirdPartyScan)
                null -> return@collectLatest
            }
            currentCoroutineContext().ensureActive()
            pending.compareAndSet(request, null)
        }
    }

    // Identity distinguishes two deliveries of the same payment and prevents an older completion
    // from consuming the replacement request.
    private class Request(
        val content: IncomingPaymentIntent
    )
}
