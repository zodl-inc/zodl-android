package co.electriccoin.zcash.ui.screen.connecthardware

import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.reflect.typeOf

/**
 * Which hardware wallet is being enrolled, and everything the shared enrollment screens need to
 * finish it. Keystone carries the UR its QR flow scanned; Ledger carries nothing, because its
 * pairing is held by `LedgerPairingRepository` until the account is imported.
 */
@Serializable
sealed interface HardwareWalletEnrollment {
    @Serializable
    data class Keystone(
        val ur: String
    ) : HardwareWalletEnrollment

    @Serializable
    data object Ledger : HardwareWalletEnrollment
}

/**
 * Lets [HardwareWalletEnrollment] travel as a type-safe navigation argument. Navigation derives
 * types for primitives and enums on its own, but a sealed type needs an explicit [NavType]; the
 * value rides as its JSON encoding, URL-encoded because it becomes a route path segment.
 */
object HardwareWalletEnrollmentNavType : NavType<HardwareWalletEnrollment>(isNullableAllowed = false) {
    val typeMap = mapOf(typeOf<HardwareWalletEnrollment>() to this)

    override fun put(
        bundle: Bundle,
        key: String,
        value: HardwareWalletEnrollment
    ) = bundle.putString(key, Json.encodeToString(value))

    override fun get(
        bundle: Bundle,
        key: String
    ): HardwareWalletEnrollment? = bundle.getString(key)?.let { Json.decodeFromString(it) }

    override fun parseValue(value: String): HardwareWalletEnrollment = Json.decodeFromString(value)

    override fun serializeAsValue(value: HardwareWalletEnrollment): String = Uri.encode(Json.encodeToString(value))
}
