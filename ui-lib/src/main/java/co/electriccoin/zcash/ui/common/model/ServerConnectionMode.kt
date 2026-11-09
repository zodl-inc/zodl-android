package co.electriccoin.zcash.ui.common.model

/**
 * How the wallet picks its lightwalletd server, as the user sees it in Choose Server.
 *
 * - [PRIVATE]: the Zodl OHTTP gateway (the former "Automatic" behaviour on this branch).
 * - [PUBLIC]: public (clearnet) servers, picked automatically; the gateway is not used.
 * - [MANUAL]: the single server the user picked.
 */
enum class ServerConnectionMode {
    PRIVATE,
    PUBLIC,
    MANUAL,
    ;

    /** Private and Public both pick the server themselves; only Manual pins an explicit one. */
    val isAutomatic: Boolean get() = this != MANUAL
}
