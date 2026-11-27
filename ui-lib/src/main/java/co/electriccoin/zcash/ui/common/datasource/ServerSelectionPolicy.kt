package co.electriccoin.zcash.ui.common.datasource

import co.electriccoin.lightwallet.client.model.LightWalletEndpoint

/**
 * Single source of truth for whether the wallet is in automatic server-selection mode (broadcast to
 * all bundled servers) or manual mode (use only the selected endpoint). Shared by
 * [co.electriccoin.zcash.ui.common.repository.AutomaticServerRepository] and the transaction
 * submission path so they can never disagree.
 *
 * A null [isAutomaticPreference] (e.g. wallets migrated from before the setting existed) is treated
 * as manual when the wallet points at a custom, non-bundled endpoint, and as automatic otherwise.
 * Treating null as automatic unconditionally would broadcast such a migrated user's transaction to
 * every bundled server, ignoring the custom endpoint they selected.
 */
internal fun resolveIsServerSelectionAutomatic(
    isAutomaticPreference: Boolean?,
    currentEndpoint: LightWalletEndpoint?,
    knownEndpoints: List<LightWalletEndpoint>
): Boolean = isAutomaticPreference ?: (currentEndpoint == null || currentEndpoint in knownEndpoints)

/**
 * Whether [endpoint] is a custom, non-bundled endpoint rather than one of [knownEndpoints]. Shared by
 * [co.electriccoin.zcash.ui.common.repository.AutomaticServerRepository] and
 * [co.electriccoin.zcash.ui.screen.chooseserver.ChooseServerVM] so they can never disagree.
 */
internal fun resolveIsEndpointCustom(
    endpoint: LightWalletEndpoint,
    knownEndpoints: List<LightWalletEndpoint>
): Boolean = endpoint !in knownEndpoints
