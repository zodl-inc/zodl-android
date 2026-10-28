package co.electriccoin.zcash.ui.common.repository

import androidx.compose.ui.graphics.Color
import cash.z.ecc.android.sdk.model.BlockHeight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds the full viewing key, the birthday, the name and the icon colour a view-only import collected
 * until the account is imported. Memory only: the key never reaches a navigation route, saved state, preferences or a
 * log, and process death drops it, after which the flow has to start again.
 */
internal interface ViewOnlyPendingImportRepository {
    fun get(): PendingViewOnlyImport?

    fun set(pendingImport: PendingViewOnlyImport)

    fun clear()
}

internal class ViewOnlyPendingImportRepositoryImpl : ViewOnlyPendingImportRepository {
    private val pending = MutableStateFlow<PendingViewOnlyImport?>(null)

    override fun get(): PendingViewOnlyImport? = pending.value

    override fun set(pendingImport: PendingViewOnlyImport) = pending.update { pendingImport }

    override fun clear() = pending.update { null }
}

/**
 * A full viewing key waiting to be imported under [name], with the icon [color] picked for it, if
 * any. [birthday] is the height the birthday screens settled on, which come before the name, and
 * null until they did.
 */
internal data class PendingViewOnlyImport(
    val ufvk: String,
    val name: String,
    val color: Color? = null,
    val birthday: BlockHeight? = null,
) {
    /**
     * Overridden so the viewing key is never printed.
     */
    override fun toString() = "PendingViewOnlyImport(ufvk=***, name=$name, color=$color, birthday=$birthday)"
}
