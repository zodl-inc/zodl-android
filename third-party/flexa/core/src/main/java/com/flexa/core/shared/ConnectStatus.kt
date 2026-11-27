package com.flexa.core.shared


data class ConnectStatus(
    val status: Status,
    var idToken: String? = null,
) {
    enum class Status {
        SDK_IS_NOT_INITIALISED,
        STRANGER,
        ANONYMOUS,
        CONNECTED,
        PENDING,
        CANCELLED
    }

    fun toConnectResult(): ConnectResult {
        return when (this.status) {
            Status.ANONYMOUS,
            Status.STRANGER,
            Status.CANCELLED -> ConnectResult(
                ConnectResult.Status.NOT_CONNECTED,
                idToken
            )
            Status.CONNECTED -> ConnectResult(
                ConnectResult.Status.CONNECTED,
                idToken
            )
            Status.PENDING -> ConnectResult(
                ConnectResult.Status.PENDING,
                idToken
            )
            Status.SDK_IS_NOT_INITIALISED -> ConnectResult(
                ConnectResult.Status.SDK_IS_NOT_INITIALIZED,
                idToken
            )
        }
    }
}

data class ConnectResult(
    val status: Status,
    var idToken: String? = null,
) {
    enum class Status(val code: String) {
        SDK_IS_NOT_INITIALIZED("sdkIsNotInitialized"),
        NOT_CONNECTED("notConnected"),
        CONNECTED("connected"),
        PENDING("pending"),
    }
}
