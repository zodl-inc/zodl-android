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
        val FALLBACK =
            VotingServiceConfig(
                version = 1,
                voteServers = listOf(ServiceEndpoint(url = "https://vote-chain-primary.valargroup.org", label = "valargroup-primary")),
                pirServers = listOf(ServiceEndpoint(url = "https://vote-chain-primary.valargroup.org", label = "valargroup-primary"))
            )
    }
}
