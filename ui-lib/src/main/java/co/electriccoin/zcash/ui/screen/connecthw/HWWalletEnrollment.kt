package co.electriccoin.zcash.ui.screen.connecthw

import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.reflect.typeOf

/**
 * Which hardware wallet is being enrolled, and everything the shared enrollment screens need to
 * finish it. Keystone carries the UR its QR flow scanned; Ledger carries nothing, because its
 * pairing is held by the Ledger feature (see `LedgerAccountImporter`) until the account is imported.
 */
@Serializable
sealed interface HWWalletEnrollment {
    @Serializable
    data class Keystone(
        val ur: String
    ) : HWWalletEnrollment

    @Serializable
    data object Ledger : HWWalletEnrollment
}

/**
 * Lets [HWWalletEnrollment] travel as a type-safe navigation argument. Navigation derives
 * types for primitives and enums on its own, but a sealed type needs an explicit [NavType]; the
 * value rides as its JSON encoding, URL-encoded because it becomes a route path segment.
 */
object HWWalletEnrollmentNavType : NavType<HWWalletEnrollment>(isNullableAllowed = false) {
    val typeMap = mapOf(typeOf<HWWalletEnrollment>() to this)

    override fun put(
        bundle: Bundle,
        key: String,
        value: HWWalletEnrollment
    ) = bundle.putString(key, Json.encodeToString(value))

    override fun get(
        bundle: Bundle,
        key: String
    ): HWWalletEnrollment? = bundle.getString(key)?.let { Json.decodeFromString(it) }

    override fun parseValue(value: String): HWWalletEnrollment = Json.decodeFromString(value)

    override fun serializeAsValue(value: HWWalletEnrollment): String = Uri.encode(Json.encodeToString(value))
}
