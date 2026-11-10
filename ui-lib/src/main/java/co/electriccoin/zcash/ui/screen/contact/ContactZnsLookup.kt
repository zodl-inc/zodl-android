package co.electriccoin.zcash.ui.screen.contact

import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.common.zns.LookUpZnsRecipientUseCase
import co.electriccoin.zcash.ui.common.zns.ZnsFieldStatus
import co.electriccoin.zcash.ui.common.zns.ZnsLookupResult
import co.electriccoin.zcash.ui.common.zns.ZnsName
import co.electriccoin.zcash.ui.common.zns.errorMessage
import co.electriccoin.zcash.ui.design.util.StringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration.Companion.milliseconds

/** The ZNS state of a contact form's address field, for the field's text [input]. */
sealed interface ContactZnsState {
    val input: String

    /** The field holds no name; it is validated as an address. */
    data class NotAName(
        override val input: String
    ) : ContactZnsState

    data class LookingUp(
        override val input: String,
        val name: ZnsName
    ) : ContactZnsState

    data class Resolved(
        override val input: String,
        val name: ZnsName,
        val address: String
    ) : ContactZnsState

    data class Failed(
        override val input: String,
        val name: ZnsName,
        val message: StringResource
    ) : ContactZnsState
}

/** The line the address field shows under itself for this state, or null when it shows none or an error. */
val ContactZnsState.fieldStatus: ZnsFieldStatus?
    get() =
        when (this) {
            is ContactZnsState.LookingUp -> ZnsFieldStatus.LookingUp
            is ContactZnsState.Resolved -> ZnsFieldStatus.Resolved(address)
            is ContactZnsState.Failed, is ContactZnsState.NotAName -> null
        }

/**
 * Looks up a ZNS name typed into a contact form's [address] field, once the user stops typing.
 *
 * The contact a form saves from a name is the resolved address, a fixed address the user approved: the wallet never
 * resolves it again, so a later change to the name cannot move payments. Every state is tied to the text it was
 * computed for, and [state] only ever describes the field's current text, so a late answer for earlier text can never
 * be saved.
 */
class ContactZnsLookup(
    lookUpZnsRecipient: LookUpZnsRecipientUseCase,
    private val address: StateFlow<String>,
    scope: CoroutineScope,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val lookups: Flow<ContactZnsState> =
        address.transformLatest { input ->
            val name = ZnsName.fromRecipientInput(input) ?: return@transformLatest
            delay(LOOKUP_DEBOUNCE)
            emit(lookUpZnsRecipient(input).toContactState(input, name))
        }

    /** The state of the field's current text: an answer only counts while the field still holds its text. */
    val state: StateFlow<ContactZnsState> =
        combine(address, lookups.onStart { emit(pendingFor("")) }) { typed, answer ->
            answer.takeIf { it.input == typed } ?: pendingFor(typed)
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = pendingFor(address.value)
        )

    /** [address] with a typed name replaced by the address it resolved to. */
    fun effectiveAddress(address: Flow<String>): Flow<String> =
        combine(address, state) { typed, zns -> (zns as? ContactZnsState.Resolved)?.address ?: typed }

    /**
     * The address Save may store for the field's current text: the text itself when it is not a name, the resolved
     * address when the name resolved, or null while the name has not resolved.
     */
    fun addressToSave(): String? {
        val typed = address.value
        if (ZnsName.fromRecipientInput(typed) == null) return typed
        return (state.value as? ContactZnsState.Resolved)?.takeIf { it.input == typed }?.address
    }

    private fun pendingFor(input: String): ContactZnsState =
        ZnsName.fromRecipientInput(input)?.let { ContactZnsState.LookingUp(input = input, name = it) }
            ?: ContactZnsState.NotAName(input)

    private fun ZnsLookupResult.toContactState(
        input: String,
        name: ZnsName
    ): ContactZnsState =
        when (this) {
            ZnsLookupResult.NotAName -> {
                ContactZnsState.NotAName(input)
            }

            is ZnsLookupResult.Resolved -> {
                ContactZnsState.Resolved(input = input, name = name, address = address)
            }

            is ZnsLookupResult.ForName -> {
                ContactZnsState.Failed(input = input, name = name, message = checkNotNull(errorMessage()))
            }
        }

    private companion object {
        val LOOKUP_DEBOUNCE = 350.milliseconds
    }
}
