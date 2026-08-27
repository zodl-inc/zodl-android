package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.preference.EncryptedPreferenceProvider
import co.electriccoin.zcash.preference.api.PreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.ui.common.model.voting.VotingErrors
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionRecoverableException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
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

    private companion object {
        const val ACCOUNT_UUID = "aabbccddeeff00112233445566778899"
        const val ROUND_ID = "1111111111111111111111111111111111111111111111111111111111111111"

        fun inMemoryEncryptedPreferences(): EncryptedPreferenceProvider {
            val provider = InMemoryPreferenceProvider()
            return mockk<EncryptedPreferenceProvider> {
                coEvery { this@mockk.invoke() } returns provider
            }
        }
    }

    private class InMemoryPreferenceProvider : PreferenceProvider {
        private val strings = mutableMapOf<String, String?>()
        private val stringSets = mutableMapOf<String, Set<String>?>()
        private val longs = mutableMapOf<String, Long?>()

        override suspend fun hasKey(key: PreferenceKey) =
            key.key in strings || key.key in stringSets || key.key in longs

        override suspend fun putString(
            key: PreferenceKey,
            value: String?
        ) {
            strings[key.key] = value
        }

        override suspend fun putStringSet(
            key: PreferenceKey,
            value: Set<String>?
        ) {
            stringSets[key.key] = value
        }

        override suspend fun putLong(
            key: PreferenceKey,
            value: Long?
        ) {
            longs[key.key] = value
        }

        override suspend fun getLong(key: PreferenceKey): Long? = longs[key.key]

        override suspend fun getString(key: PreferenceKey): String? = strings[key.key]

        override suspend fun getStringSet(key: PreferenceKey): Set<String>? = stringSets[key.key]

        override fun observe(key: PreferenceKey): Flow<String?> = flowOf(strings[key.key])

        override suspend fun remove(key: PreferenceKey) {
            strings.remove(key.key)
            stringSets.remove(key.key)
            longs.remove(key.key)
        }

        override suspend fun clearPreferences(): Boolean {
            strings.clear()
            stringSets.clear()
            longs.clear()
            return true
        }
    }
}
