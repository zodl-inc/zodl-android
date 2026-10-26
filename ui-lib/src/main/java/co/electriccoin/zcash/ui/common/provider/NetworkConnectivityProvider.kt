package co.electriccoin.zcash.ui.common.provider

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

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
}

class NetworkConnectivityProviderImpl(
    private val context: Context
) : NetworkConnectivityProvider {
    override fun isOnline(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true // If we can't get the service, assume online (fail open).

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
            return capabilities?.let {
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } ?: false
        } else {
            @Suppress("DEPRECATION")
            return connectivityManager.activeNetworkInfo?.isConnected == true
        }
    }
}
