package co.electriccoin.zcash.ui.common.usecase

class PersistEndpointException(
    message: String?,
    cause: Throwable? = null
) : Exception(message, cause)
