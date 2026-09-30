package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.preference.EncryptedPreferenceProvider
import co.electriccoin.zcash.preference.api.PreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.ui.common.model.voting.VotingErrors
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionRecoverableException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class VotingRecoveryRepositoryImplTest {
    private val repository = VotingRecoveryRepositoryImpl(inMemoryEncryptedPreferences())

    @Test
    fun conflictingSelectionRaisesTypedConflictError() =
        runTest {
            repository.storeProposalSelections(
                accountUuid = ACCOUNT_UUID,
                roundId = ROUND_ID,
                proposalSelections = mapOf(1 to VotingProposalSelection(choiceId = 0, numOptions = 2))
            )

            val failure =
                assertFailsWith<VotingSubmissionRecoverableException> {
                    repository.storeProposalSelections(
                        accountUuid = ACCOUNT_UUID,
                        roundId = ROUND_ID,
                        proposalSelections = mapOf(1 to VotingProposalSelection(choiceId = 1, numOptions = 2))
                    )
                }

            val conflict = assertIs<VotingErrors.ConflictingProposalSelection>(failure.failure)
            assertEquals(ROUND_ID, conflict.roundId)
            assertEquals(1, conflict.proposalId)
            assertEquals(
                mapOf(1 to VotingProposalSelection(choiceId = 0, numOptions = 2)),
                repository.get(ACCOUNT_UUID, ROUND_ID)?.proposalSelections
            )
        }

    @Test
    fun restoringIdenticalSelectionIsIdempotent() =
        runTest {
            val selections = mapOf(1 to VotingProposalSelection(choiceId = 0, numOptions = 2))

            repository.storeProposalSelections(ACCOUNT_UUID, ROUND_ID, selections)
            repository.storeProposalSelections(ACCOUNT_UUID, ROUND_ID, selections)

            assertEquals(selections, repository.get(ACCOUNT_UUID, ROUND_ID)?.proposalSelections)
        }

    @Test
    fun selectionsForDistinctProposalsAccumulate() =
        runTest {
            repository.storeProposalSelections(
                ACCOUNT_UUID,
                ROUND_ID,
                mapOf(1 to VotingProposalSelection(choiceId = 0, numOptions = 2))
            )
            repository.storeProposalSelections(
                ACCOUNT_UUID,
                ROUND_ID,
                mapOf(2 to VotingProposalSelection(choiceId = 1, numOptions = 2))
            )

            assertEquals(
                mapOf(
                    1 to VotingProposalSelection(choiceId = 0, numOptions = 2),
                    2 to VotingProposalSelection(choiceId = 1, numOptions = 2)
                ),
                repository.get(ACCOUNT_UUID, ROUND_ID)?.proposalSelections
            )
        }

    @Test
    fun trimmedFieldsRoundTripThroughJson() =
        runTest {
            repository.storeBundleSetup(
                accountUuid = ACCOUNT_UUID,
                roundId = ROUND_ID,
                bundleCount = 2,
                eligibleWeight = 300L,
                bundleWeights = listOf(200L, 100L),
                trimmedBundleCount = 3,
                trimmedWeight = 12L
            )

            val restored = repository.get(ACCOUNT_UUID, ROUND_ID)

            assertEquals(3, restored?.trimmedBundleCount)
            assertEquals(12L, restored?.trimmedWeight)
            assertEquals(2, restored?.bundleCount)
            assertEquals(listOf(200L, 100L), restored?.bundleWeights)
        }

    @Test
    fun untrimmedSetupRoundTripsAsZeroTrimmedFields() =
        runTest {
            repository.storeBundleSetup(
                accountUuid = ACCOUNT_UUID,
                roundId = ROUND_ID,
                bundleCount = 2,
                eligibleWeight = 300L,
                bundleWeights = listOf(200L, 100L)
            )

            val restored = repository.get(ACCOUNT_UUID, ROUND_ID)

            assertEquals(0, restored?.trimmedBundleCount)
            assertEquals(0L, restored?.trimmedWeight)
        }

    @Test
    fun concurrentMutatorsKeepEveryUpdate() =
        runTest {
            val repository = VotingRecoveryRepositoryImpl(inMemoryEncryptedPreferences(interleaveWrites = true))

            withContext(Dispatchers.Default) {
                coroutineScope {
                    (1..CONCURRENT_PROPOSALS).forEach { proposalId ->
                        launch {
                            repository.storeProposalSelections(
                                accountUuid = ACCOUNT_UUID,
                                roundId = ROUND_ID,
                                proposalSelections =
                                    mapOf(proposalId to VotingProposalSelection(choiceId = 0, numOptions = 2))
                            )
                        }
                        launch {
                            repository.markProposalSubmitted(
                                accountUuid = ACCOUNT_UUID,
                                roundId = ROUND_ID,
                                proposalId = proposalId
                            )
                        }
                    }
                }
            }

            val restored = repository.get(ACCOUNT_UUID, ROUND_ID)

            assertEquals((1..CONCURRENT_PROPOSALS).toSet(), restored?.proposalSelections?.keys)
            assertEquals((1..CONCURRENT_PROPOSALS).toSet(), restored?.submittedProposalIds)
        }

    private companion object {
        const val ACCOUNT_UUID = "aabbccddeeff00112233445566778899"
        const val ROUND_ID = "1111111111111111111111111111111111111111111111111111111111111111"
        const val CONCURRENT_PROPOSALS = 20

        fun inMemoryEncryptedPreferences(interleaveWrites: Boolean = false): EncryptedPreferenceProvider {
            val provider = InMemoryPreferenceProvider(interleaveWrites)
            return mockk<EncryptedPreferenceProvider> {
                coEvery { this@mockk.invoke() } returns provider
            }
        }
    }

    /**
     * [interleaveWrites] suspends every write before it lands, which is the window a lost update
     * needs: a second mutator gets to read the snapshot the first one is about to replace.
     */
    private class InMemoryPreferenceProvider(
        private val interleaveWrites: Boolean = false
    ) : PreferenceProvider {
        private val lock = Any()
        private val strings = mutableMapOf<String, String?>()
        private val stringSets = mutableMapOf<String, Set<String>?>()
        private val longs = mutableMapOf<String, Long?>()

        override suspend fun hasKey(key: PreferenceKey) =
            synchronized(lock) { key.key in strings || key.key in stringSets || key.key in longs }

        override suspend fun putString(
            key: PreferenceKey,
            value: String?
        ) {
            if (interleaveWrites) {
                yield()
            }
            synchronized(lock) { strings[key.key] = value }
        }

        override suspend fun putStringSet(
            key: PreferenceKey,
            value: Set<String>?
        ) {
            synchronized(lock) { stringSets[key.key] = value }
        }

        override suspend fun putLong(
            key: PreferenceKey,
            value: Long?
        ) {
            synchronized(lock) { longs[key.key] = value }
        }

        override suspend fun getLong(key: PreferenceKey): Long? = synchronized(lock) { longs[key.key] }

        override suspend fun getString(key: PreferenceKey): String? = synchronized(lock) { strings[key.key] }

        override suspend fun getStringSet(key: PreferenceKey): Set<String>? =
            synchronized(lock) { stringSets[key.key] }

        override fun observe(key: PreferenceKey): Flow<String?> = flowOf(synchronized(lock) { strings[key.key] })

        override suspend fun remove(key: PreferenceKey) {
            synchronized(lock) {
                strings.remove(key.key)
                stringSets.remove(key.key)
                longs.remove(key.key)
            }
        }

        override suspend fun clearPreferences(): Boolean {
            synchronized(lock) {
                strings.clear()
                stringSets.clear()
                longs.clear()
            }
            return true
        }
    }
}
