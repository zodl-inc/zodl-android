package co.electriccoin.zcash.ui.common.provider

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * Checks whether the device has network connectivity. Used to exclude offline scenarios from gateway
 * health checks: offline failures should not count as gateway outages.
 */
interface NetworkConnectivityProvider {
    /**
     * Returns true if the device has an active network connection (cellular, Wi-Fi, etc.), false if
     * the device is offline.
     */
    fun isOnline(): Boolean

    /**
     * Emits whether the device is online now and on every change. The default only reports the current state, for
     * implementations that cannot observe changes.
     */
    fun observeOnline(): Flow<Boolean> = flow { emit(isOnline()) }
}

class NetworkConnectivityProviderImpl(
    private val context: Context
) : NetworkConnectivityProvider {
    override fun observeOnline(): Flow<Boolean> =
        callbackFlow {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (manager == null) {
                trySend(true)
                awaitClose { }
                return@callbackFlow
            }
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        trySend(isOnline())
                    }

                    override fun onLost(network: Network) {
                        trySend(isOnline())
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities
                    ) {
                        trySend(isOnline())
                    }
                }
            trySend(isOnline())
            manager.registerDefaultNetworkCallback(callback)
            awaitClose { manager.unregisterNetworkCallback(callback) }
        }.distinctUntilChanged()

    override fun isOnline(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager == null) {
            // If we can't get the service, assume online (fail open).
            return true
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
            capabilities?.let {
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } ?: false
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected == true
        }
    }
}
