package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.voting.CommitmentTreeLatest
import co.electriccoin.zcash.ui.common.model.voting.CommitmentTreeLeafBlock
import co.electriccoin.zcash.ui.common.model.voting.CommitmentTreeLeafPage
import kotlinx.coroutines.delay
import java.util.Base64

internal suspend fun findVanCommitmentPosition(
    roundId: String,
    startHeight: Long = 0,
    expectedVanCmx: ByteArray,
    maxRecoveryAttempts: Int = SPENT_NULLIFIER_RECOVERY_ATTEMPTS,
    recoveryDelayMillis: Long = SPENT_NULLIFIER_RECOVERY_POLL_MS,
    maxPagesPerAttempt: Int = COMMITMENT_TREE_MAX_PAGES,
    fetchLatest: suspend (String) -> CommitmentTreeLatest,
    fetchLeafPage: suspend (String, Long, Long) -> CommitmentTreeLeafPage
): Int? {
    val position =
        findCommitmentLeafPositions(
            roundId = roundId,
            startHeight = startHeight,
            expectedLeaves = listOf(expectedVanCmx),
            maxRecoveryAttempts = maxRecoveryAttempts,
            recoveryDelayMillis = recoveryDelayMillis,
            maxPagesPerAttempt = maxPagesPerAttempt,
            fetchLatest = fetchLatest,
            fetchLeafPage = fetchLeafPage
        ).single() ?: return null
    require(position <= Int.MAX_VALUE) { "VAN position exceeds supported range: $position" }
    return position.toInt()
}

/**
 * Scans the round's commitment tree for the positions of [expectedLeaves] in a single
 * paginated pass per attempt. Returns one position per requested leaf, in request order,
 * with null for a leaf that is absent from the fully scanned tree. The scan is retried
 * until every leaf is found or the attempt budget is exhausted.
 */
internal suspend fun findCommitmentLeafPositions(
    roundId: String,
    startHeight: Long = 0,
    expectedLeaves: List<ByteArray>,
    maxRecoveryAttempts: Int = SPENT_NULLIFIER_RECOVERY_ATTEMPTS,
    recoveryDelayMillis: Long = SPENT_NULLIFIER_RECOVERY_POLL_MS,
    maxPagesPerAttempt: Int = COMMITMENT_TREE_MAX_PAGES,
    fetchLatest: suspend (String) -> CommitmentTreeLatest,
    fetchLeafPage: suspend (String, Long, Long) -> CommitmentTreeLeafPage
): List<Long?> {
    require(roundId.isNotBlank()) { "roundId must not be blank" }
    require(startHeight >= 0) { "startHeight must be non-negative" }
    require(expectedLeaves.isNotEmpty()) { "expectedLeaves must not be empty" }
    expectedLeaves.forEach { expectedLeaf ->
        require(expectedLeaf.size == COMMITMENT_BYTES) {
            "Expected commitment leaves must be $COMMITMENT_BYTES bytes"
        }
    }
    require(maxRecoveryAttempts >= 1) { "maxRecoveryAttempts must be >= 1" }
    require(recoveryDelayMillis >= 0) { "recoveryDelayMillis must be non-negative" }
    require(maxPagesPerAttempt >= 1) { "maxPagesPerAttempt must be >= 1" }

    var lastPositions: List<Long?> = List(expectedLeaves.size) { null }
    repeat(maxRecoveryAttempts) { attempt ->
        val latest = fetchLatest(roundId)
        require(latest.height >= 0) { "Commitment tree height must be non-negative" }
        require(latest.nextIndex >= 0) { "Commitment tree next_index must be non-negative" }
        val scanStartHeight = startHeight.takeIf { it <= latest.height } ?: 0L
        val matches = List(expectedLeaves.size) { mutableSetOf<Long>() }
        var previousNextIndex: Long? = null
        var previousBlockHeight: Long? = null
        var pageStart = scanStartHeight
        var pageCount = 0
        do {
            check(pageCount < maxPagesPerAttempt) {
                "Commitment tree pagination exceeded $maxPagesPerAttempt pages"
            }
            pageCount += 1
            val page = fetchLeafPage(roundId, pageStart, latest.height)
            page.blocks.forEach { block ->
                validateCommitmentLeafBlock(
                    block = block,
                    pageStart = pageStart,
                    latestHeight = latest.height,
                    previousNextIndex = previousNextIndex,
                    previousBlockHeight = previousBlockHeight
                )
                block.leavesBase64.forEachIndexed { leafOffset, encodedLeaf ->
                    val leaf = decodeCommitmentLeaf(encodedLeaf)
                    expectedLeaves.forEachIndexed { targetIndex, expectedLeaf ->
                        if (leaf.contentEquals(expectedLeaf)) {
                            matches[targetIndex] += Math.addExact(block.startIndex, leafOffset.toLong())
                        }
                    }
                }
                previousNextIndex = Math.addExact(block.startIndex, block.leavesBase64.size.toLong())
                previousBlockHeight = block.height
            }
            validateCommitmentPageCursor(
                nextFromHeight = page.nextFromHeight,
                pageStart = pageStart,
                latestHeight = latest.height,
                lastBlockHeight = page.blocks.lastOrNull()?.height
            )
            pageStart = page.nextFromHeight
        } while (pageStart != 0L)

        val scannedNextIndex = previousNextIndex ?: 0L
        require(scannedNextIndex == latest.nextIndex) {
            "Commitment tree scan ended at index $scannedNextIndex, expected ${latest.nextIndex}"
        }

        matches.forEach { positions ->
            require(positions.size <= 1) {
                "Requested commitment leaf appears more than once in the round tree"
            }
        }
        val positions = matches.map { it.singleOrNull() }
        if (positions.all { position -> position != null }) {
            return positions
        }
        lastPositions = positions
        if (attempt + 1 < maxRecoveryAttempts) {
            delay(recoveryDelayMillis)
        }
    }
    return lastPositions
}

private fun validateCommitmentLeafBlock(
    block: CommitmentTreeLeafBlock,
    pageStart: Long,
    latestHeight: Long,
    previousNextIndex: Long?,
    previousBlockHeight: Long?
) {
    require(block.height in pageStart..latestHeight) {
        "Commitment leaf block ${block.height} is outside requested range $pageStart..$latestHeight"
    }
    require(previousBlockHeight == null || block.height > previousBlockHeight) {
        "Commitment leaf block heights must be strictly increasing"
    }
    require(block.startIndex >= 0) { "Commitment leaf start_index must be non-negative" }
    if (previousNextIndex == null) {
        require(block.startIndex == 0L) {
            "First commitment leaf block must start at index 0"
        }
    } else {
        require(block.startIndex == previousNextIndex) {
            "Commitment leaf start_index ${block.startIndex} does not continue at $previousNextIndex"
        }
    }
}

private fun validateCommitmentPageCursor(
    nextFromHeight: Long,
    pageStart: Long,
    latestHeight: Long,
    lastBlockHeight: Long?
) {
    require(nextFromHeight == 0L || nextFromHeight > pageStart) {
        "Commitment tree next_from_height must advance or be zero"
    }
    require(nextFromHeight == 0L || nextFromHeight <= latestHeight) {
        "Commitment tree next_from_height exceeds latest height"
    }
    if (nextFromHeight != 0L && lastBlockHeight != null) {
        require(nextFromHeight > lastBlockHeight) {
            "Commitment tree next_from_height must follow the last returned block"
        }
    }
}

private fun decodeCommitmentLeaf(encodedLeaf: String): ByteArray =
    runCatching {
        Base64.getDecoder().decode(encodedLeaf).also { decoded ->
            require(decoded.size == COMMITMENT_BYTES) {
                "Commitment leaf must be $COMMITMENT_BYTES bytes"
            }
        }
    }.getOrElse { throw IllegalArgumentException("Malformed commitment leaf", it) }

private const val COMMITMENT_BYTES = 32
private const val COMMITMENT_TREE_MAX_PAGES = 128
