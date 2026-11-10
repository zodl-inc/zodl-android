package co.electriccoin.zcash.ui.common.zns

import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.provider.IsZnsNamesEnabledStorageProvider
import co.electriccoin.zcash.ui.common.usecase.ValidateAddressUseCase
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.CancellationException

/** What a recipient field's text means for ZNS. */
sealed interface ZnsLookupResult {
    /** The text is not a ZNS name; it is validated as an address. */
    data object NotAName : ZnsLookupResult

    /** The text is a ZNS name. */
    sealed interface ForName : ZnsLookupResult {
        val name: ZnsName
    }

    /** The user has not turned Zcash names on. No request was made. */
    data class NamesOff(
        override val name: ZnsName
    ) : ForName

    data class NotRegistered(
        override val name: ZnsName
    ) : ForName

    /** The name points to an address this wallet rejects. */
    data class InvalidAddress(
        override val name: ZnsName
    ) : ForName

    /** No resolver answered, or its answer could not be trusted. */
    data class Unreachable(
        override val name: ZnsName
    ) : ForName

    data class Resolved(
        override val name: ZnsName,
        val address: String,
        val type: AddressType,
    ) : ForName
}

/** The message a recipient field shows under itself for this result, or null when it shows none. */
fun ZnsLookupResult.ForName.errorMessage(): StringResource? =
    when (this) {
        is ZnsLookupResult.NamesOff -> stringRes(R.string.zns_error_off)
        is ZnsLookupResult.NotRegistered -> stringRes(R.string.zns_error_notRegistered)
        is ZnsLookupResult.InvalidAddress -> stringRes(R.string.zns_error_invalidAddress)
        is ZnsLookupResult.Unreachable -> stringRes(R.string.zns_error_unreachable)
        is ZnsLookupResult.Resolved -> null
    }

/** The outcome of looking a send's name up again right before the send. */
enum class ZnsConfirmation {
    /** The name still points to the address the proposal pays. */
    UNCHANGED,

    /** The name now points elsewhere, or was released. */
    CHANGED,

    /** The name service could not confirm the name. */
    UNCONFIRMED
}

/**
 * Looks up the ZNS name in a recipient field. Shared by the send form and the contact forms. Never throws, apart from
 * cancellation.
 */
class LookUpZnsRecipientUseCase(
    private val resolver: ZnsNameResolver,
    private val isZnsNamesEnabled: IsZnsNamesEnabledStorageProvider,
    private val validateAddress: ValidateAddressUseCase,
) {
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    suspend operator fun invoke(input: String): ZnsLookupResult {
        val name = ZnsName.fromRecipientInput(input) ?: return ZnsLookupResult.NotAName
        if (!isZnsNamesEnabled.get()) return ZnsLookupResult.NamesOff(name)
        val registration =
            try {
                resolver.resolve(name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Twig.info { "ZNS: lookup failed: ${e.message}" }
                return ZnsLookupResult.Unreachable(name)
            } ?: return ZnsLookupResult.NotRegistered(name)
        val type =
            try {
                validateAddress(registration.address)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return ZnsLookupResult.InvalidAddress(name)
            }
        return if (type.isNotValid) {
            ZnsLookupResult.InvalidAddress(name)
        } else {
            ZnsLookupResult.Resolved(name = name, address = registration.address, type = type)
        }
    }

    /**
     * Looks [name] up again and checks that it still points to [address].
     *
     * This reads the same downloaded copy of the index as the first lookup, and downloads it again only once it is
     * older than its maximum age (5 minutes). A download at the moment of every send would tell the name service when
     * a send is about to happen. So a name updated or released within that window plus the resolver's sync lag is not
     * caught here; a send can then still go to the address the name pointed to, but never to an address it did not.
     */
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    suspend fun confirm(
        name: ZnsName,
        address: String
    ): ZnsConfirmation {
        if (!isZnsNamesEnabled.get()) return ZnsConfirmation.UNCONFIRMED
        val registration =
            try {
                resolver.resolve(name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Twig.info { "ZNS: confirmation failed: ${e.message}" }
                return ZnsConfirmation.UNCONFIRMED
            }
        return if (registration?.address == address) ZnsConfirmation.UNCHANGED else ZnsConfirmation.CHANGED
    }
}
