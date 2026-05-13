package co.electriccoin.zcash.ui.common.model

import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import org.json.JSONObject

data class ServerSelection(
    val mode: ConnectionMode,
    val endpoint: LightWalletEndpoint? = null,
    val isCustom: Boolean = false
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
                put(KEY_ENDPOINT_IS_CUSTOM, isCustom)
            }
        }

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_ENDPOINT_HOST = "endpoint_host"
        private const val KEY_ENDPOINT_PORT = "endpoint_port"
        private const val KEY_ENDPOINT_IS_SECURE = "endpoint_is_secure"
        private const val KEY_ENDPOINT_IS_CUSTOM = "endpoint_is_custom"

        fun automatic() = ServerSelection(ConnectionMode.AUTOMATIC)

        fun manual(endpoint: LightWalletEndpoint, isCustom: Boolean) =
            ServerSelection(
                mode = ConnectionMode.MANUAL,
                endpoint = endpoint,
                isCustom = isCustom
            )

        fun fromPersistedEndpoint(
            endpoint: LightWalletEndpoint?,
            knownEndpoints: List<LightWalletEndpoint>
        ) = endpoint?.let {
            if (knownEndpoints.contains(it)) {
                automatic()
            } else {
                manual(endpoint = it, isCustom = true)
            }
        } ?: automatic()

        fun from(jsonObject: JSONObject): ServerSelection {
            val mode =
                ConnectionMode.fromPersistedValue(
                    jsonObject.getString(KEY_MODE)
                )

            return when (mode) {
                ConnectionMode.AUTOMATIC -> {
                    automatic()
                }

                ConnectionMode.MANUAL -> {
                    manual(
                        endpoint = jsonObject.getEndpoint(),
                        isCustom = jsonObject.optBoolean(KEY_ENDPOINT_IS_CUSTOM, false)
                    )
                }
            }
        }

        fun fromPersistedJson(json: String): ServerSelection? =
            runCatching { from(JSONObject(json)) }
                .getOrNull()

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
