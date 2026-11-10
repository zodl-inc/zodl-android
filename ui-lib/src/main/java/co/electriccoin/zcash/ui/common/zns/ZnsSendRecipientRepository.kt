package co.electriccoin.zcash.ui.common.zns

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A send to a ZNS name: the [name] the user typed and the resolved [address] the proposal pays. */
data class ZnsSendRecipient(
    val name: ZnsName,
    val address: String,
)

/** Why a send to a name was stopped right before it went out. Nothing was sent. */
data class ZnsSendStopped(
    val recipient: ZnsSendRecipient,
    val confirmation: ZnsConfirmation,
)

/**
 * Carries the name of the send being prepared from the send form to review and to the check right before sending.
 * Held in memory only. The name is display information: the proposal itself only ever holds the resolved address.
 */
class ZnsSendRecipientRepository {
    private val mutableRecipient = MutableStateFlow<ZnsSendRecipient?>(null)
    private val mutableStopped = MutableStateFlow<ZnsSendStopped?>(null)

    /** The name of the send being prepared, or null when the recipient was not typed as a name. */
    val recipient: StateFlow<ZnsSendRecipient?> = mutableRecipient.asStateFlow()

    /** The last send to a name that was stopped, until the send form takes it. */
    val stopped: StateFlow<ZnsSendStopped?> = mutableStopped.asStateFlow()

    fun setRecipient(recipient: ZnsSendRecipient?) = mutableRecipient.update { recipient }

    /** The name the review screen shows for [address], or null when the send to it was not typed as a name. */
    fun nameFor(address: String): ZnsName? = recipient.value?.takeIf { it.address == address }?.name

    fun reportStopped(stopped: ZnsSendStopped) {
        mutableRecipient.update { null }
        mutableStopped.update { stopped }
    }

    /** Returns and forgets the last stopped send. */
    fun takeStopped(): ZnsSendStopped? = mutableStopped.getAndSetNull()

    private fun <T> MutableStateFlow<T?>.getAndSetNull(): T? {
        while (true) {
            val current = value
            if (compareAndSet(current, null)) return current
        }
    }
}
