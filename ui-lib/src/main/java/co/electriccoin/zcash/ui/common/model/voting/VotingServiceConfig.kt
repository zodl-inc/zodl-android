package co.electriccoin.zcash.ui.common.model.voting

import kotlinx.serialization.Serializable

@Serializable
data class VotingServiceConfig(
    val version: Int,
    val voteServers: List<ServiceEndpoint>,
    val pirServers: List<ServiceEndpoint>
) {
    @Serializable
    data class ServiceEndpoint(
        val url: String,
        val label: String
    )

    companion object {
        // 10.0.2.2 = host machine from Android emulator; use real device IP for physical device
        val FALLBACK =
            VotingServiceConfig(
                version = 1,
                voteServers = listOf(ServiceEndpoint(url = "http://10.0.2.2:1317", label = "local")),
                pirServers = listOf(ServiceEndpoint(url = "http://10.0.2.2:3000", label = "local-pir"))
            )
    }
}
