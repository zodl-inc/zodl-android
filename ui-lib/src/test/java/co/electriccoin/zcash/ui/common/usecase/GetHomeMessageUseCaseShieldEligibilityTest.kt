package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccount
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The shield-funds home message is offered only to an account that can spend, since shielding spends
 * its transparent funds; a view-only account holding the same transparent balance never gets it.
 */
class GetHomeMessageUseCaseShieldEligibilityTest {
    private val sdkAccount = Account.new(AccountUuid.new(ByteArray(16) { it.toByte() }))

    @Test
    fun aSpendableAccountWithEnoughTransparentFundsIsOfferedShielding() {
        assertTrue(isShieldFundsMessageEligible(zodl(SHIELDABLE)))
    }

    @Test
    fun aSpendableAccountWithTooLittleTransparentFundsIsNotOfferedShielding() {
        assertFalse(isShieldFundsMessageEligible(zodl(DUST)))
    }

    @Test
    fun aViewOnlyAccountIsNeverOfferedShielding() {
        assertFalse(isShieldFundsMessageEligible(viewOnly(SHIELDABLE)))
        assertFalse(isShieldFundsMessageEligible(viewOnly(DUST)))
    }

    private fun zodl(transparent: Zatoshi) =
        ZodlAccount(
            sdkAccount = sdkAccount,
            unifiedAddress = "u",
            transparentAddress = "t",
            saplingAddress = "s",
            orchardBalance = null,
            saplingBalance = null,
            ironwoodBalance = null,
            transparentBalance = transparent,
            isSelected = true,
        )

    private fun viewOnly(transparent: Zatoshi) =
        ViewOnlyAccount(
            sdkAccount = sdkAccount,
            unifiedAddress = "u",
            transparentAddress = "t",
            orchardBalance = null,
            saplingBalance = null,
            ironwoodBalance = null,
            transparentBalance = transparent,
            isSelected = true,
            name = stringRes("Savings"),
            customName = "Savings",
            icon = 0,
        )
}

private val SHIELDABLE = Zatoshi(200_000L)

private val DUST = Zatoshi(1_000L)
