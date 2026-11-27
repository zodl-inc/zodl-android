package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.model.Account

/**
 * The encoded viewing key of the given [type] as the SDK stores it for this account, or null when the
 * account does not carry that key.
 */
fun Account.viewingKey(type: VKType): String? =
    when (type) {
        VKType.INCOMING -> uivk
        VKType.FULL -> ufvk
    }
