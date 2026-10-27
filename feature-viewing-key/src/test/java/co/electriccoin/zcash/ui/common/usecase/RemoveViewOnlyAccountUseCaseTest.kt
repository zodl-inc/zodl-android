package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.migration.MigrationAppHooks
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.BiometricRepository
import co.electriccoin.zcash.ui.common.repository.BiometricsCancelledException
import co.electriccoin.zcash.ui.common.repository.BiometricsFailureException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Removing a view-only account authenticates first, cancels its migration work, deletes it and, when
 * it was selected after the authentication, selects the Zodl account. A failed or dismissed
 * authentication removes nothing, and an id that names the Zodl account or nothing at all removes
 * nothing without asking. The account's metadata is never touched: the use case has no access to it.
 */
class RemoveViewOnlyAccountUseCaseTest {
    private val zodl = ViewOnlyAccountFixtures.zodl(isSelected = false)

    private val accountDataSource =
        mockk<AccountDataSource>(relaxed = true) {
            coEvery { getZashiAccount() } returns zodl
        }

    private val biometricRepository = mockk<BiometricRepository>(relaxed = true)

    private val migrationAppHooks = mockk<MigrationAppHooks>(relaxed = true)

    @Test
    fun aSelectedAccountIsRemovedAfterAuthenticationAndTheZodlAccountIsSelected() =
        runTest {
            val viewOnly = ViewOnlyAccountFixtures.viewOnly(isSelected = true)

            val removed = useCase(listOf(zodl, viewOnly))(ViewOnlyAccountFixtures.viewOnlyKeyId)

            assertTrue(removed)
            coVerifyOrder {
                biometricRepository.requestBiometrics(any())
                migrationAppHooks.cancelMigrationWork(ViewOnlyAccountFixtures.viewOnlyKeyId)
                accountDataSource.deleteAccount(viewOnly)
                accountDataSource.selectAccount(zodl)
            }
        }

    @Test
    fun anAccountThatWasNotSelectedLeavesTheSelectionAlone() =
        runTest {
            val viewOnly = ViewOnlyAccountFixtures.viewOnly(isSelected = false)

            useCase(listOf(ViewOnlyAccountFixtures.zodl(isSelected = true), viewOnly))(
                ViewOnlyAccountFixtures.viewOnlyKeyId
            )

            coVerify(exactly = 1) { accountDataSource.deleteAccount(viewOnly) }
            coVerify(exactly = 0) { accountDataSource.selectAccount(any<WalletAccount>()) }
        }

    @Test
    fun anAccountSelectedWhileAuthenticatingIsReplacedByTheZodlAccount() =
        runTest {
            val snapshot = ViewOnlyAccountFixtures.viewOnly(isSelected = false)

            useCase(
                accounts = listOf(ViewOnlyAccountFixtures.zodl(isSelected = true), snapshot),
                current = listOf(zodl, ViewOnlyAccountFixtures.viewOnly(isSelected = true)),
            )(ViewOnlyAccountFixtures.viewOnlyKeyId)

            coVerifyOrder {
                accountDataSource.getAllAccounts()
                accountDataSource.deleteAccount(snapshot)
                accountDataSource.selectAccount(zodl)
            }
        }

    @Test
    fun anAccountDeselectedWhileAuthenticatingLeavesTheSelectionAlone() =
        runTest {
            val snapshot = ViewOnlyAccountFixtures.viewOnly(isSelected = true)

            useCase(
                accounts = listOf(zodl, snapshot),
                current = listOf(ViewOnlyAccountFixtures.zodl(isSelected = true), ViewOnlyAccountFixtures.viewOnly()),
            )(ViewOnlyAccountFixtures.viewOnlyKeyId)

            coVerify(exactly = 1) { accountDataSource.deleteAccount(snapshot) }
            coVerify(exactly = 0) { accountDataSource.selectAccount(any<WalletAccount>()) }
        }

    @Test
    fun aDismissedAuthenticationRemovesNothing() =
        runTest {
            coEvery { biometricRepository.requestBiometrics(any()) } throws BiometricsCancelledException()

            assertFailsWith<BiometricsCancelledException> {
                useCase(listOf(zodl, ViewOnlyAccountFixtures.viewOnly()))(ViewOnlyAccountFixtures.viewOnlyKeyId)
            }

            coVerify(exactly = 0) { accountDataSource.deleteAccount(any()) }
            coVerify(exactly = 0) { migrationAppHooks.cancelMigrationWork(any()) }
        }

    @Test
    fun aFailedAuthenticationRemovesNothing() =
        runTest {
            coEvery { biometricRepository.requestBiometrics(any()) } throws BiometricsFailureException()

            assertFailsWith<BiometricsFailureException> {
                useCase(listOf(zodl, ViewOnlyAccountFixtures.viewOnly()))(ViewOnlyAccountFixtures.viewOnlyKeyId)
            }

            coVerify(exactly = 0) { accountDataSource.deleteAccount(any()) }
        }

    @Test
    fun theZodlAccountIsNeverRemoved() =
        runTest {
            val removed = useCase(listOf(zodl, ViewOnlyAccountFixtures.viewOnly()))(ViewOnlyAccountFixtures.zodlKeyId)

            assertFalse(removed)
            coVerify(exactly = 0) { biometricRepository.requestBiometrics(any()) }
            coVerify(exactly = 0) { accountDataSource.deleteAccount(any()) }
        }

    @Test
    fun aMissingAccountRemovesNothing() =
        runTest {
            val removed = useCase(listOf(zodl))(ViewOnlyAccountFixtures.viewOnlyKeyId)

            assertFalse(removed)
            coVerify(exactly = 0) { biometricRepository.requestBiometrics(any()) }
            coVerify(exactly = 0) { accountDataSource.deleteAccount(any()) }
        }

    /**
     * [accounts] is what the account is resolved from before the authentication, [current] what
     * the data source holds after it.
     */
    private fun useCase(
        accounts: List<WalletAccount>,
        current: List<WalletAccount> = accounts,
    ): RemoveViewOnlyAccountUseCase {
        coEvery { accountDataSource.getAllAccounts() } returns current
        return RemoveViewOnlyAccountUseCase(
            getViewOnlyAccount =
                GetViewOnlyAccountUseCase(ViewOnlyAccountFixtures.getWalletAccounts(MutableStateFlow(accounts))),
            accountDataSource = accountDataSource,
            biometricRepository = biometricRepository,
            migrationAppHooks = migrationAppHooks,
        )
    }
}
