package co.electriccoin.zcash.ui.common.model

data object LceLoading

data class LceError(
    val cause: Throwable,
    val restart: () -> Unit,
    val dismiss: () -> Unit,
)

data class Lce<out T>(
    val content: T? = null,
    val loading: LceLoading? = null,
    val error: LceError? = null,
)
