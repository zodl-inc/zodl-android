package co.electriccoin.zcash.ui.common.usecase

import androidx.compose.ui.graphics.Color
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccount
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.common.model.metadata.MetadataV3
import co.electriccoin.zcash.ui.common.usecase.AccountIconFixtures.PICKED
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every account's colour comes from the repository's shared metadata map, empty at first, an account
 * without a colour left out; a view-only icon takes its colour, the default circle without one; the
 * Zodl account shows its own drawable until it has a colour, then its mark on a circle in it. A
 * hardware wallet always shows its own drawable, an account never takes another account's colour,
 * and a metadata change that leaves every colour as it was emits nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveAccountIconUseCaseTest {
    private val metadata = MutableStateFlow<Map<AccountUuid, MetadataV3>>(emptyMap())

    private val useCase = AccountIconFixtures.useCase(metadata)

    @Test
    fun theColorsStartEmptyThenFollowTheRepository() =
        runTest {
            val emitted = mutableListOf<Map<AccountUuid, Color>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase.observeAll().toList(emitted) }

            metadata.value =
                mapOf(
                    VIEW_ONLY_UUID to AccountIconFixtures.metadata(Color(PICKED)),
                    ZODL_UUID to AccountIconFixtures.metadata(null)
                )
            metadata.value = mapOf(VIEW_ONLY_UUID to AccountIconFixtures.metadata(Color(OTHER)))

            assertEquals(
                listOf(emptyMap(), mapOf(VIEW_ONLY_UUID to Color(PICKED)), mapOf(VIEW_ONLY_UUID to Color(OTHER))),
                emitted
            )
        }

    @Test
    fun aViewOnlyIconTakesItsColor() {
        assertEquals(
            AccountIconFixtures.viewOnlyIcon(Color(PICKED)),
            useCase.iconOf(mapOf(VIEW_ONLY_UUID to Color(PICKED)), viewOnly())
        )
    }

    @Test
    fun aColoredZodlIconIsItsMarkOnACircleInTheColor() {
        assertEquals(
            ImageResource.Layered(
                background = Color(PICKED),
                foreground = ImageResource.ByDrawable(R.drawable.ic_item_zodl_glyph)
            ),
            useCase.iconOf(mapOf(ZODL_UUID to Color(PICKED)), zodl())
        )
    }

    @Test
    fun iconOfFallsBackToTheDefaultIcon() {
        assertEquals(AccountIconFixtures.viewOnlyIcon(), useCase.iconOf(emptyMap(), viewOnly()))
        assertEquals(imageRes(R.drawable.ic_item_zashi), useCase.iconOf(emptyMap(), zodl()))
    }

    @Test
    fun hardwareWalletsKeepTheirDrawableWhateverTheColor() {
        val colors = mapOf(EditAccountFixtures.hardwareUuid to Color(PICKED))

        listOf(EditAccountFixtures.keystone(), EditAccountFixtures.ledger()).forEach { account ->
            assertEquals(imageRes(account.icon), useCase.iconOf(account, Color(PICKED)))
            assertEquals(imageRes(account.icon), useCase.iconOf(colors, account))
            assertEquals(imageRes(account.icon), useCase.iconOf(account, null))
        }
    }

    @Test
    fun anotherAccountsColorIsNotApplied() {
        val colors = mapOf(OTHER_UUID to Color(PICKED))

        assertEquals(AccountIconFixtures.viewOnlyIcon(), useCase.iconOf(colors, viewOnly()))
        assertEquals(imageRes(R.drawable.ic_item_zashi), useCase.iconOf(colors, zodl()))
    }

    @Test
    fun aMetadataChangeThatIsNotAColorEmitsNothing() =
        runTest {
            val emitted = mutableListOf<Map<AccountUuid, Color>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase.observeAll().toList(emitted) }
            val colored = AccountIconFixtures.metadata(Color(PICKED))

            metadata.value = mapOf(VIEW_ONLY_UUID to colored)
            metadata.value =
                mapOf(
                    VIEW_ONLY_UUID to
                        colored.copy(accountMetadata = colored.accountMetadata.copy(read = listOf("tx1"))),
                    ZODL_UUID to AccountIconFixtures.metadata(null)
                )

            assertEquals(listOf(emptyMap(), mapOf(VIEW_ONLY_UUID to Color(PICKED))), emitted)
        }

    private fun zodl() =
        ZodlAccount(
            sdkAccount = Account.new(ZODL_UUID),
            unifiedAddress = "u",
            transparentAddress = "t",
            saplingAddress = "s",
            orchardBalance = null,
            saplingBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = true,
        )

    private fun viewOnly() =
        ViewOnlyAccount(
            sdkAccount = Account.new(VIEW_ONLY_UUID),
            unifiedAddress = "u",
            transparentAddress = "t",
            orchardBalance = null,
            saplingBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = false,
            name = stringRes("Savings"),
            customName = "Savings",
            icon = AccountIconFixtures.VIEW_ONLY_ICON,
        )

    private companion object {
        val ZODL_UUID: AccountUuid = AccountUuid.new(ByteArray(16) { it.toByte() })
        val VIEW_ONLY_UUID: AccountUuid = AccountUuid.new(ByteArray(16) { (it + 1).toByte() })
        val OTHER_UUID: AccountUuid = AccountUuid.new(ByteArray(16) { (it + 3).toByte() })
        const val OTHER = 0xFF0088FF
    }
}
