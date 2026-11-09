package com.flexa.core.shared

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

sealed class ConnectionState {
    data object Available : ConnectionState()
    data object Unavailable : ConnectionState()
}

private val Context.currentConnectivityState: ConnectionState
    get() {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return getCurrentConnectionState(connectivityManager)
    }

private fun getCurrentConnectionState(
    connectivityManager: ConnectivityManager
): ConnectionState {
    val network = connectivityManager.activeNetwork
    val actNetwork = runCatching { connectivityManager.getNetworkCapabilities(network) }
    val connected =
        actNetwork.getOrNull()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    return if (connected) ConnectionState.Available else ConnectionState.Unavailable
}

fun Context.observeConnectionAsFlow() = callbackFlow {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val callback = networkCallback { connectionState -> trySend(connectionState) }
    val networkRequest = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .build()
    connectivityManager.registerNetworkCallback(networkRequest, callback)
    awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
}

private val typesSet = HashSet<String>(2)
fun networkCallback(callback: (ConnectionState) -> Unit): ConnectivityManager.NetworkCallback {
    return object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            typesSet.add(network.toString())
            callback(ConnectionState.Available)
        }

        override fun onLost(network: Network) {
            typesSet.remove(network.toString())
            if (typesSet.isEmpty()) callback(ConnectionState.Unavailable)
        }
    }
}

@ExperimentalCoroutinesApi
@Composable
fun connectionState(): State<ConnectionState> {
    val context = LocalContext.current
    val previewMode = LocalInspectionMode.current
    val state = if (!previewMode) produceState(initialValue = context.currentConnectivityState) {
        context.observeConnectionAsFlow().collect { value = it }
    } else remember { mutableStateOf(ConnectionState.Available) }
    return state
}
