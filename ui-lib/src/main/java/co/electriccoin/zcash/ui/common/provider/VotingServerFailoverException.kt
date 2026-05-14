package co.electriccoin.zcash.ui.common.provider

internal class VotingServerFailoverException(
    val path: String,
    val serverUrls: List<String>,
    val lastError: Throwable?
) : IllegalStateException(
        "All configured vote servers failed for $path",
        lastError
    )
