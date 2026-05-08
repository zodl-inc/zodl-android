package co.electriccoin.zcash.ui.common.model

import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import org.json.JSONObject

data class ServerSelection(
    val mode: ConnectionMode,
    val endpoint: LightWalletEndpoint? = null
) {
    init {
        require(mode == ConnectionMode.AUTOMATIC || endpoint != null) {
            "Manual server selection requires an endpoint"
        }
    }

    fun toJson() =
        JSONObject().apply {
            put(KEY_MODE, mode.persistedValue)
            endpoint?.let {
                put(KEY_ENDPOINT_HOST, it.host)
                put(KEY_ENDPOINT_PORT, it.port)
                put(KEY_ENDPOINT_IS_SECURE, it.isSecure)
            }
        }

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_ENDPOINT_HOST = "endpoint_host"
        private const val KEY_ENDPOINT_PORT = "endpoint_port"
        private const val KEY_ENDPOINT_IS_SECURE = "endpoint_is_secure"

        fun automatic() = ServerSelection(ConnectionMode.AUTOMATIC)

        fun manual(endpoint: LightWalletEndpoint) =
            ServerSelection(
                mode = ConnectionMode.MANUAL,
                endpoint = endpoint
            )

        fun from(jsonObject: JSONObject): ServerSelection {
            val mode =
                ConnectionMode.fromPersistedValue(
                    jsonObject.getString(KEY_MODE)
                )

            return when (mode) {
                ConnectionMode.AUTOMATIC -> automatic()
                ConnectionMode.MANUAL -> manual(jsonObject.getEndpoint())
            }
        }

        private fun JSONObject.getEndpoint() =
            LightWalletEndpoint(
                host = getString(KEY_ENDPOINT_HOST),
                port = getInt(KEY_ENDPOINT_PORT),
                isSecure = getBoolean(KEY_ENDPOINT_IS_SECURE)
            )
    }
}

enum class ConnectionMode(
    val persistedValue: String
) {
    AUTOMATIC("automatic"),
    MANUAL("manual");

    companion object {
        fun fromPersistedValue(value: String) =
            entries.firstOrNull { it.persistedValue == value } ?: AUTOMATIC
    }
}
