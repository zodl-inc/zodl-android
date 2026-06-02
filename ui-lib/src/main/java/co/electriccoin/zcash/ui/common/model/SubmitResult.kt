package co.electriccoin.zcash.ui.common.model

sealed interface SubmitResult {
    val txIds: List<String>

    data class Success(
        override val txIds: List<String>
    ) : SubmitResult

    data class Failure(
        override val txIds: List<String>,
        val code: Int,
        val description: String?
    ) : NonResubmittableError

    data class GrpcFailure(
        override val txIds: List<String>
    ) : SubmitResult

    data class Partial(
        override val txIds: List<String>,
        val statuses: List<String>
    ) : SubmitResult

    data class Error(
        val cause: Exception
    ) : NonResubmittableError {
        override val txIds: List<String> = emptyList()
    }

    sealed interface NonResubmittableError : SubmitResult
}
