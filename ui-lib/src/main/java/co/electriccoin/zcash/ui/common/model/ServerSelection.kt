package co.electriccoin.zcash.ui.common.model

import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

    fun toJson() = serverSelectionJson.encodeToString(toPersisted())

    companion object {
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
            when {
                it == knownEndpoints.firstOrNull() -> automatic()
                knownEndpoints.contains(it) -> manual(endpoint = it, isCustom = false)
                else -> manual(endpoint = it, isCustom = true)
            }
        } ?: automatic()

        fun from(json: String): ServerSelection =
            serverSelectionJson.decodeFromString<PersistedServerSelection>(json).toDomain()

        fun fromPersistedJson(json: String): ServerSelection? =
            runCatching { from(json) }
                .getOrNull()
    }
}

@Serializable
private data class PersistedServerSelection(
    val mode: String,
    @SerialName("endpoint_host")
    val endpointHost: String? = null,
    @SerialName("endpoint_port")
    val endpointPort: Int? = null,
    @SerialName("endpoint_is_secure")
    val endpointIsSecure: Boolean? = null,
    @SerialName("endpoint_is_custom")
    val endpointIsCustom: Boolean? = null
) {
    fun toDomain(): ServerSelection =
        when (ConnectionMode.fromPersistedValue(mode)) {
            ConnectionMode.AUTOMATIC -> {
                ServerSelection.automatic()
            }

            ConnectionMode.MANUAL -> {
                ServerSelection.manual(
                    endpoint =
                        LightWalletEndpoint(
                            host = requireNotNull(endpointHost),
                            port = requireNotNull(endpointPort),
                            isSecure = requireNotNull(endpointIsSecure)
                        ),
                    isCustom = endpointIsCustom ?: false
                )
            }
        }
}

private fun ServerSelection.toPersisted() =
    PersistedServerSelection(
        mode = mode.persistedValue,
        endpointHost = endpoint?.host,
        endpointPort = endpoint?.port,
        endpointIsSecure = endpoint?.isSecure,
        endpointIsCustom = endpoint?.let { isCustom }
    )

private val serverSelectionJson =
    Json {
        ignoreUnknownKeys = true
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
