package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.exception.InitializeException
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * A rename stores the trimmed name for the account through the account data source, and a blank
 * name is never stored; a rename the SDK refuses fails.
 */
class RenameViewOnlyAccountUseCaseTest {
    private val accountDataSource = mockk<AccountDataSource>(relaxed = true)

    private val useCase = RenameViewOnlyAccountUseCase(accountDataSource)

    private val account = EditAccountFixtures.viewOnly()

    @Test
    fun theTrimmedNameIsStoredForTheAccount() =
        runTest {
            useCase(account, "  Savings ")

            coVerify(exactly = 1) { accountDataSource.renameAccount(account, "Savings") }
        }

    @Test
    fun aLongNameIsCappedByCodePointsWithoutSplittingAnEmoji() =
        runTest {
            useCase(account, "Vault " + LOCK.repeat(7))

            coVerify(exactly = 1) {
                accountDataSource.renameAccount(account, "Vault " + LOCK.repeat(6))
            }
        }

    @Test
    fun aJoinerSequenceCutAtTheLimitLeavesNoDanglingJoiner() =
        runTest {
            useCase(account, "Our homes " + FAMILY)

            coVerify(exactly = 1) {
                accountDataSource.renameAccount(account, "Our homes " + MAN)
            }
        }

    @Test
    fun aBlankNameIsIgnored() =
        runTest {
            useCase(account, "   ")

            coVerify(exactly = 0) { accountDataSource.renameAccount(any(), any()) }
        }

    @Test
    fun aRenameTheSdkRefusesFails() =
        runTest {
            coEvery { accountDataSource.renameAccount(any(), any()) } throws
                InitializeException.RenameAccountException(RuntimeException("database locked"))

            assertFailsWith<InitializeException.RenameAccountException> { useCase(account, "Savings") }
        }
}

private const val LOCK = "\uD83D\uDD10"

private const val MAN = "\uD83D\uDC68"

private const val WOMAN = "\uD83D\uDC69"

private const val GIRL = "\uD83D\uDC67"

private const val ZWJ = "\u200D"

private const val FAMILY = MAN + ZWJ + WOMAN + ZWJ + GIRL
