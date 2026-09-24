package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.VotingRoundSession
import cash.z.ecc.android.sdk.model.voting.VotingDelegationInputs
import cash.z.ecc.android.sdk.model.voting.VotingRoundRunReport
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Regression coverage for the account-reuse bug Milan's review of PR #6 found: keying the
 * retained session on [roundId] alone let a Keystone account switch on the same round silently
 * reuse the PREVIOUS account's session -- getKeystoneSigningRequests handed out the wrong
 * account's PCZTs, and setBallotIntents/runToCompletion wrote the new account's choices into the
 * old account's round state. [VotingKeystoneSessionHolder] has otherwise had no test coverage.
 */
class VotingKeystoneSessionHolderTest {
    @Test
    fun `a second call for the same account and round reuses the open session`() =
        runTest {
            val env = environment()
            val holder = env.holder()

            env.ensureDelegationPipeline(holder, roundId = ROUND_ID, accountUuidString = ACCOUNT_A)
            env.ensureDelegationPipeline(holder, roundId = ROUND_ID, accountUuidString = ACCOUNT_A)

            coVerify(exactly = 1) { env.votingCryptoClient.openVotingDb(any()) }
            coVerify(exactly = 1) { env.roundSessionA.run(any(), null) }
        }

    @Test
    fun `switching Keystone accounts on the same round closes the old session and opens a fresh one`() =
        runTest {
            val env = environment()
            val holder = env.holder()

            env.ensureDelegationPipeline(holder, roundId = ROUND_ID, accountUuidString = ACCOUNT_A)
            env.ensureDelegationPipeline(holder, roundId = ROUND_ID, accountUuidString = ACCOUNT_B)

            // Two distinct sessions opened -- account B's call must NOT have been treated as a
            // no-op reuse of account A's still-open session.
            coVerify(exactly = 1) { env.roundSessionA.run(any(), null) }
            coVerify(exactly = 1) { env.roundSessionB.run(any(), null) }

            // Account A's session (and its voting DB handle) is torn down before B's is opened --
            // otherwise the two would contend for the shared native (dbPath, walletId) lock.
            coVerifyOrder {
                env.roundSessionA.close()
                env.votingCryptoClient.closeVotingDb(DB_HANDLE_A)
                env.votingCryptoClient.openVotingDb(any())
            }

            // getKeystoneSigningRequests after the switch must reach B's session, not A's stale
            // one -- the concrete symptom Milan's review reported ("hands out A's PCZTs").
            holder.getKeystoneSigningRequests(ROUND_ID, listOf(0))
            coVerify(exactly = 1) { env.roundSessionB.getKeystoneSigningRequests(listOf(0)) }
            coVerify(exactly = 0) { env.roundSessionA.getKeystoneSigningRequests(any()) }
        }

    private class Environment(
        val votingCryptoClient: VotingCryptoClient,
        val synchronizerProvider: SynchronizerProvider,
        val roundSessionA: VotingRoundSession,
        val roundSessionB: VotingRoundSession
    ) {
        fun holder() = VotingKeystoneSessionHolder(votingCryptoClient, synchronizerProvider)

        suspend fun ensureDelegationPipeline(
            holder: VotingKeystoneSessionHolder,
            roundId: String,
            accountUuidString: String
        ) = holder.ensureDelegationPipeline(
            roundId = roundId,
            votingDbPath = "/tmp/voting.sqlite3",
            accountUuidString = accountUuidString,
            networkId = 0,
            proposals = emptyList(),
            hotkeySecret = ByteArray(0),
            chainEndpoints = listOf("https://chain.example"),
            ceremonyStartSeconds = 0L,
            voteEndTimeSeconds = 0L,
            delegationInputs = mockk<VotingDelegationInputs>()
        )
    }

    private fun environment(): Environment {
        val synchronizer = mockk<Synchronizer>()
        coEvery { synchronizer.getVotingTorRuntimeHandle() } returns TOR_RUNTIME
        coEvery { synchronizer.releaseVotingTorRuntimeHandle() } returns Unit

        val synchronizerProvider = mockk<SynchronizerProvider>()
        coEvery { synchronizerProvider.getSynchronizer() } returns synchronizer

        val roundSessionA = mockk<VotingRoundSession>(relaxed = true)
        coEvery { roundSessionA.run(any(), null) } returns mockk<VotingRoundRunReport>()
        val roundSessionB = mockk<VotingRoundSession>(relaxed = true)
        coEvery { roundSessionB.run(any(), null) } returns mockk<VotingRoundRunReport>()

        val votingCryptoClient = mockk<VotingCryptoClient>()
        coEvery { votingCryptoClient.openVotingDb(any()) } returnsMany listOf(DB_HANDLE_A, DB_HANDLE_B)
        coEvery { votingCryptoClient.setWalletId(any(), any(), any()) } returns Unit
        coEvery { votingCryptoClient.closeVotingDb(any()) } returns Unit
        coEvery {
            votingCryptoClient.openRoundSession(
                dbHandle = DB_HANDLE_A,
                torRuntime = any(),
                roundId = any(),
                proposals = any(),
                hotkeySecret = any(),
                chainEndpoints = any(),
                operationEpoch = any(),
                configuredHelperUrls = any(),
                voteTreeNodeUrls = any(),
                ceremonyStartSeconds = any(),
                voteEndTimeSeconds = any()
            )
        } returns roundSessionA
        coEvery {
            votingCryptoClient.openRoundSession(
                dbHandle = DB_HANDLE_B,
                torRuntime = any(),
                roundId = any(),
                proposals = any(),
                hotkeySecret = any(),
                chainEndpoints = any(),
                operationEpoch = any(),
                configuredHelperUrls = any(),
                voteTreeNodeUrls = any(),
                ceremonyStartSeconds = any(),
                voteEndTimeSeconds = any()
            )
        } returns roundSessionB

        return Environment(votingCryptoClient, synchronizerProvider, roundSessionA, roundSessionB)
    }

    private companion object {
        const val ROUND_ID = "round-id"
        const val ACCOUNT_A = "account-a"
        const val ACCOUNT_B = "account-b"
        const val DB_HANDLE_A = 11L
        const val DB_HANDLE_B = 22L
        const val TOR_RUNTIME = 7L
    }
}
