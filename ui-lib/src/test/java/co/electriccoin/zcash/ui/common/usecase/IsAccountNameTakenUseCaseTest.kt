package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.WalletAccount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A name is taken when another account shows it, the Zodl and hardware accounts' names included,
 * compared in its saved form and ignoring case; the account being renamed never counts, and a blank
 * name is never taken. The observed answer is paired with the name it was computed for and follows
 * both the typed name and the other accounts' names.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IsAccountNameTakenUseCaseTest {
    private val accounts =
        MutableStateFlow<List<WalletAccount>?>(
            listOf(EditAccountFixtures.zodl(), EditAccountFixtures.viewOnly(), EditAccountFixtures.keystone())
        )

    private val isTaken = EditAccountFixtures.isAccountNameTaken(accounts)

    @Test
    fun aNameAnotherAccountShowsIsTakenInAnyCase() =
        runTest {
            assertTrue(isTaken("Zodl"))
            assertTrue(isTaken("zodl"))
            assertTrue(isTaken("KEYSTONE", excludingAccountKeyId = VAULT))
        }

    @Test
    fun surroundingWhitespaceIsIgnoredOnBothSides() =
        runTest {
            assertTrue(isTaken("  zodl vault "))
            accounts.value = listOf(EditAccountFixtures.viewOnly(customName = " savings  "))
            assertTrue(isTaken("Savings"))
        }

    @Test
    fun aNameCappedToAnotherAccountsNameIsTaken() =
        runTest {
            accounts.value = listOf(EditAccountFixtures.viewOnly(customName = "Zodl Savings"))
            assertTrue(isTaken("Zodl Savings 2026"))
        }

    @Test
    fun theAccountsOwnNameIsNotTaken() =
        runTest {
            assertFalse(isTaken("Zodl Vault", excludingAccountKeyId = VAULT))
            assertFalse(isTaken("ZODL VAULT", excludingAccountKeyId = VAULT))
        }

    @Test
    fun aUniqueOrBlankNameIsNotTaken() =
        runTest {
            assertFalse(isTaken("Savings"))
            assertFalse(isTaken("Zodl 2"))
            accounts.value = listOf(EditAccountFixtures.viewOnly(customName = ""))
            assertFalse(isTaken("   "))
            accounts.value = emptyList()
            assertFalse(isTaken("Zodl"))
        }

    @Test
    fun theObservedAnswerFollowsTheTypedNameAndCarriesIt() =
        runTest {
            val typed = MutableStateFlow("Savings")
            val answers = mutableListOf<NameCheck>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                isTaken.observe(typed, excludingAccountKeyId = VAULT).collect { answers.add(it) }
            }

            typed.value = "zodl"
            typed.value = "Zodl Vault"

            assertEquals(
                listOf(
                    NameCheck(name = "Savings", isTaken = false),
                    NameCheck(name = "zodl", isTaken = true),
                    NameCheck(name = "Zodl Vault", isTaken = false),
                ),
                answers
            )
        }

    @Test
    fun theObservedAnswerFollowsTheOtherAccountsNames() =
        runTest {
            val answers = mutableListOf<NameCheck>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                isTaken.observe(flowOf("Zodl Savings")).collect { answers.add(it) }
            }

            accounts.value = listOf(EditAccountFixtures.viewOnly(customName = "zodl savings"))

            assertEquals(listOf(false, true), answers.map { it.isTaken })
        }

    @Test
    fun nothingIsObservedBeforeTheAccountsLoad() =
        runTest {
            accounts.value = null
            val answers = mutableListOf<NameCheck>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                isTaken.observe(flowOf("Zodl")).collect { answers.add(it) }
            }

            assertEquals(emptyList(), answers)

            accounts.value = listOf(EditAccountFixtures.zodl())

            assertEquals(listOf(NameCheck(name = "Zodl", isTaken = true)), answers)
        }

    private companion object {
        val VAULT = EditAccountFixtures.viewOnlyKeyId
    }
}
