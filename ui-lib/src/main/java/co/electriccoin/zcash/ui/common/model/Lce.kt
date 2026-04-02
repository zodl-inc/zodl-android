package co.electriccoin.zcash.ui.common.model

sealed interface LceContent<out T> {
    data class Success<out T>(
        val value: T
    ) : LceContent<T>

    data class Error(
        val cause: Throwable,
        val restart: () -> Unit,
        val dismiss: () -> Unit,
    ) : LceContent<Nothing>
}

data class Lce<out T>(
    val loading: Boolean = false,
    val content: LceContent<T>? = null,
) {
    val success: T? = (content as? LceContent.Success<T>)?.value
    val error: LceContent.Error? = content as? LceContent.Error
}
