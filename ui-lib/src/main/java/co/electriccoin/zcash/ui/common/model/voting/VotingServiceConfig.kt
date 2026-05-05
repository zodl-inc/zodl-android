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
        val ENDPOINT =
            ServiceEndpoint(
                url = "https://vote-chain-primary.valargroup.org",
                label = "valargroup-primary"
            )
        val SERVERS =
            VotingServiceConfig(
                version = 1,
                voteServers = listOf(ENDPOINT),
                pirServers = listOf(ENDPOINT),
            )
    }
}
