package co.electriccoin.zcash.ui.common.model

/**
 * Which of an account's unified viewing keys gets exported: the incoming one (`uivk1…`, incoming activity
 * only) or the full one (`uview1…`, every transaction past and future).
 */
enum class VKType {
    INCOMING,
    FULL
}
