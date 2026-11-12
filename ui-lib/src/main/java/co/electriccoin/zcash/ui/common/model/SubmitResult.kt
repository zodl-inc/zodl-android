package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.model.SubmitRejection

sealed interface SubmitResult {
    val txIds: List<String>

    data class Success(
        override val txIds: List<String>
    ) : SubmitResult

    data class Failure(
        override val txIds: List<String>,
        val code: Int,
        val description: String?,
        /** Why the server refused the transaction for good, or null when the SDK does not know it to be final. */
        val rejection: SubmitRejection? = null
    ) : NonResubmittableError

    data class GrpcFailure(
        override val txIds: List<String>,
        val description: String? = null,
        val reason: Reason? = null
    ) : SubmitResult {
        enum class Reason {
            TIMEOUT
        }
    }

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
