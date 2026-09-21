package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.Account
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.VKType
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [GetVKUseCase] maps the selected account's SDK keys to the export flow's [VKType]s:
 * FULL is the `uview1…` UFVK, INCOMING the `uivk1…` UIVK, and availability mirrors their nullability.
 * A selected Keystone account exports its own keys the same way (MOB-1892).
 */
class GetVKUseCaseTest {
    @Test
    fun fullMapsToUfvkAndIncomingToUivk() =
        runTest {
            val useCase = useCase(ufvk = "uview1full", uivk = "uivk1incoming")

            assertEquals("uview1full", useCase.observe(VKType.FULL).first())
            assertEquals("uivk1incoming", useCase.observe(VKType.INCOMING).first())
        }

    @Test
    fun missingKeysObserveAsNull() =
        runTest {
            val useCase = useCase(ufvk = null, uivk = null)

            assertNull(useCase.observe(VKType.FULL).first())
            assertNull(useCase.observe(VKType.INCOMING).first())
        }

    @Test
    fun availabilityReflectsWhichKeysArePresent() =
        runTest {
            val useCase = useCase(ufvk = null, uivk = "uivk1incoming")

            assertEquals(
                mapOf(VKType.INCOMING to true, VKType.FULL to false),
                useCase.observeAvailability().first()
            )
        }

    @Test
    fun keystoneAccountExportsItsOwnKeys() =
        runTest {
            val useCase = useCase(ufvk = "uview1keystone", uivk = "uivk1keystone", isKeystone = true)

            assertEquals("uview1keystone", useCase.observe(VKType.FULL).first())
            assertEquals("uivk1keystone", useCase.observe(VKType.INCOMING).first())
            assertEquals(
                mapOf(VKType.INCOMING to true, VKType.FULL to true),
                useCase.observeAvailability().first()
            )
        }

    private fun useCase(
        ufvk: String?,
        uivk: String?,
        isKeystone: Boolean = false,
    ): GetVKUseCase {
        val sdkAccount =
            mockk<Account> {
                every { this@mockk.ufvk } returns ufvk
                every { this@mockk.uivk } returns uivk
            }
        val account: WalletAccount =
            if (isKeystone) {
                KeystoneAccount(
                    sdkAccount = sdkAccount,
                    unifiedAddress = "unified",
                    transparentAddress = "transparent",
                    orchardBalance = null,
                    ironwoodBalance = null,
                    transparentBalance = null,
                    isSelected = true,
                )
            } else {
                ZashiAccount(
                    sdkAccount = sdkAccount,
                    unifiedAddress = "unified",
                    transparentAddress = "transparent",
                    saplingAddress = "sapling",
                    orchardBalance = null,
                    saplingBalance = null,
                    ironwoodBalance = null,
                    transparentBalance = null,
                    isSelected = true,
                )
            }
        val accountDataSource =
            mockk<AccountDataSource> {
                every { selectedAccount } returns MutableStateFlow<WalletAccount?>(account)
            }
        return GetVKUseCase(ObserveSelectedWalletAccountUseCase(accountDataSource))
    }
}
