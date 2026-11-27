package com.flexa.identity.shared

sealed class ConnectResult {
    class Connected(val idToken: String) : ConnectResult()
    class NotConnected(val message: String) : ConnectResult()
}
