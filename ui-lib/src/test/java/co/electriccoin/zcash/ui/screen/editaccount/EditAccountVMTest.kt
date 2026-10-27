package co.electriccoin.zcash.ui.screen.editaccount

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import cash.z.ecc.android.sdk.model.AccountUuid
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.AccountNameLimit
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import co.electriccoin.zcash.ui.common.usecase.AccountIconFixtures
import co.electriccoin.zcash.ui.common.usecase.EditAccountFixtures
import co.electriccoin.zcash.ui.common.usecase.NavigateToColorPickerUseCase
import co.electriccoin.zcash.ui.common.usecase.RenameViewOnlyAccountUseCase
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyContent
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyNavigator
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.colorpicker.ColorPickerArgs
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import io.mockk.MockKMatcherScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import co.electriccoin.zcash.ui.R as UiR

/**
 * For a view-only account the screen starts with the account's name, caps it at twelve characters
 * as it is typed, enables Save only for a new non-blank name or a new colour, saves once however
 * often Save is tapped and returns, and opens the removal sheet. For the Zodl account the name field
 * is disabled and shows the Zodl name, nothing renames it, there is no removal, and Save follows the
 * colour alone. The avatar follows the picks live and keeps the last one however the picker closes,
 * without pinning the stored colour it opened on. Back cancels a running save; a colour that was not
 * saved shows the error and keeps the pick; an id that names neither account kind goes straight back.
 * A failed rename writes no colour, a retry after an unsaved colour writes only the colour, remove and
 * a second avatar tap do nothing while a save runs or the picker is open, and an account removed while
 * it is edited goes back on Save. A name another account shows, the Zodl account's in any case
 * included, disables Save and says so under the field, never next to another name; the account's own
 * name never does. The keyboard's Done key saves only while Save is enabled.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
class EditAccountVMTest {
    private val navigationRouter = mockk<NavigationRouter>(relaxed = true)

    private val accountDataSource = mockk<AccountDataSource>(relaxed = true)

    private val navigateToError = mockk<NavigateToErrorUseCase>(relaxed = true)

    private val viewOnlyNavigator = mockk<ViewOnlyNavigator>(relaxed = true)

    private val viewOnlyContent =
        mockk<ViewOnlyContent> {
            every { removeAccountInfoIcon } returns REMOVE_ICON
            every { removeAccountInfo } returns REMOVE_INFO
            every { removeAccountButton } returns REMOVE_BUTTON
        }

    private val pickerRequests = mutableListOf<ColorPickerArgs>()

    private val navigateToColorPicker =
        NavigateToColorPickerUseCase(
            mockk(relaxed = true) {
                every { forward(*anyVararg()) } answers {
                    (args[0] as Array<*>).filterIsInstance<ColorPickerArgs>().forEach { pickerRequests.add(it) }
                }
            }
        )

    private val storedColors = MutableStateFlow<Map<AccountUuid, Color>>(emptyMap())

    private val metadataRepository =
        mockk<MetadataRepository>(relaxed = true) {
            every { observeColor(any()) } answers { storedColors.map { it[firstArg()] } }
            coEvery { setAccountColor(any(), any()) } returns true
        }

    private val accounts =
        MutableStateFlow<List<WalletAccount>?>(
            listOf(EditAccountFixtures.zodl(), EditAccountFixtures.viewOnly(), EditAccountFixtures.keystone())
        )

    private val isAccountNameTaken = EditAccountFixtures.isAccountNameTaken(accounts)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theFieldStartsWithTheAccountsNameAndSaveIsDisabled() =
        runTest {
            val vm = vm()
            collect(vm)

            val state = assertNotNull(vm.state.value)
            assertEquals("Zodl Vault", state.field.value.text())
            assertTrue(state.field.isEnabled)
            assertEquals(listOf(10, AccountNameLimit.MAX_LENGTH), assertNotNull(state.counter).args())
            assertFalse(state.saveButton.isEnabled)
            val remove = assertNotNull(state.remove)
            assertTrue(remove.button.isEnabled)
            assertEquals(REMOVE_BUTTON, remove.button.text)
            assertEquals(REMOVE_INFO, remove.info)
        }

    @Test
    fun anAccountShowingTheDefaultNameStartsWithAnEmptyField() =
        runTest {
            accounts.value = listOf(EditAccountFixtures.viewOnly(customName = null))
            val vm = vm()
            collect(vm)

            assertEquals("", assertNotNull(vm.state.value).field.value.text())
        }

    @Test
    fun aLongerNameIsCutToTwelveCharacters() =
        runTest {
            val vm = vm()
            collect(vm)

            type(vm, "Zodl Savings 2026")

            val state = assertNotNull(vm.state.value)
            assertEquals("Zodl Savings", state.field.value.text())
            assertEquals(listOf(12, 12), assertNotNull(state.counter).args())
        }

    @Test
    fun emojiCountOnceAndAreNeverCutInHalf() =
        runTest {
            val vm = vm()
            collect(vm)

            type(vm, LOCK.repeat(13))

            val state = assertNotNull(vm.state.value)
            assertEquals(LOCK.repeat(12), state.field.value.text())
            assertEquals(listOf(12, 12), assertNotNull(state.counter).args())
        }

    @Test
    fun saveNeedsANewNameThatIsNotBlank() =
        runTest {
            val vm = vm()
            collect(vm)

            type(vm, "   ")
            assertFalse(assertNotNull(vm.state.value).saveButton.isEnabled)

            type(vm, " Zodl Vault ")
            assertFalse(assertNotNull(vm.state.value).saveButton.isEnabled)

            type(vm, "Savings")
            assertTrue(assertNotNull(vm.state.value).saveButton.isEnabled)
        }

    @Test
    fun saveStoresTheTrimmedNameAndReturns() =
        runTest {
            val vm = vm()
            collect(vm)
            type(vm, " Savings ")

            assertNotNull(vm.state.value).saveButton.onClick()
            runCurrent()

            coVerify(exactly = 1) { accountDataSource.renameAccount(viewOnlyAccount(), "Savings") }
            verify(exactly = 1) { navigationRouter.back() }
        }

    @Test
    fun keepingTheCurrentNameIsNotADuplicate() =
        runTest {
            val vm = vm()
            collect(vm)
            assertNull(assertNotNull(vm.state.value).field.error)

            type(vm, " zodl vault ")

            val state = assertNotNull(vm.state.value)
            assertNull(state.field.error)
            assertTrue(state.saveButton.isEnabled)
            state.saveButton.onClick()
            runCurrent()
            coVerify(exactly = 1) { accountDataSource.renameAccount(viewOnlyAccount(), "zodl vault") }
        }

    @Test
    fun anotherAccountsNameDisablesSaveAndShowsTheError() =
        runTest {
            val vm = vm()
            collect(vm)
            pick(vm, Color(PICKED))

            type(vm, "Keystone")

            val state = assertNotNull(vm.state.value)
            assertFalse(state.saveButton.isEnabled)
            assertEquals(R.string.accountName_duplicate, (state.field.error as StringResource.ByResource).resource)
            state.saveButton.onClick()
            runCurrent()
            coVerify(exactly = 0) { accountDataSource.renameAccount(any(), any()) }
            coVerify(exactly = 0) { metadataRepository.setAccountColor(any(), any()) }
            verify(exactly = 0) { navigationRouter.back() }

            type(vm, "Keystone 2")
            assertNull(assertNotNull(vm.state.value).field.error)
            assertTrue(assertNotNull(vm.state.value).saveButton.isEnabled)
        }

    @Test
    fun theDuplicateErrorAlwaysBelongsToTheNameShown() =
        runTest {
            val vm = vm()
            val states = mutableListOf<EditAccountState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.state.collect { state -> state?.let { states.add(it) } }
            }
            runCurrent()

            listOf("Keystone", "Savings", "zodl", "Zodl Vault").forEach { type(vm, it) }

            assertTrue(states.isNotEmpty())
            states.forEach { state ->
                val shown = state.field.value.text()
                val isDuplicate = shown.trim().lowercase() in setOf("keystone", "zodl")
                assertEquals(isDuplicate, state.field.error != null, shown)
            }
        }

    @Test
    fun doneOnTheKeyboardDoesNothingForABlankOrDuplicateName() =
        runTest {
            val vm = vm()
            collect(vm)

            type(vm, "   ")
            assertNotNull(vm.state.value).nameField.onDone()
            runCurrent()
            type(vm, "Keystone")
            assertNotNull(vm.state.value).nameField.onDone()
            runCurrent()

            coVerify(exactly = 0) { accountDataSource.renameAccount(any(), any()) }
            verify(exactly = 0) { navigationRouter.back() }
        }

    @Test
    fun doneOnTheKeyboardSavesWhileSaveIsEnabled() =
        runTest {
            val vm = vm()
            collect(vm)
            type(vm, "Savings")

            assertNotNull(vm.state.value).nameField.onDone()
            runCurrent()

            coVerify(exactly = 1) { accountDataSource.renameAccount(viewOnlyAccount(), "Savings") }
            verify(exactly = 1) { navigationRouter.back() }
        }

    @Test
    fun theZodlNameIsADuplicateInAnyCase() =
        runTest {
            val vm = vm()
            collect(vm)

            listOf("zodl", "ZODL", " ZoDl ").forEach { name ->
                type(vm, name)

                val state = assertNotNull(vm.state.value)
                assertFalse(state.saveButton.isEnabled, name)
                assertNotNull(state.field.error, name)
            }
        }

    @Test
    fun theSavedNameReachesTheScreenThroughTheAccounts() =
        runTest {
            val vm = vm()
            collect(vm)
            type(vm, "Savings")

            accounts.value = listOf(EditAccountFixtures.viewOnly(customName = "Savings"))
            runCurrent()

            assertFalse(assertNotNull(vm.state.value).saveButton.isEnabled)
        }

    @Test
    fun aFailedSaveShowsTheErrorAndStaysOnTheScreen() =
        runTest {
            val failure = IllegalStateException("prefs")
            coEvery { accountDataSource.renameAccount(any(), any()) } throws failure
            val vm = vm()
            collect(vm)
            type(vm, "Savings")

            assertNotNull(vm.state.value).saveButton.onClick()
            runCurrent()

            verify(exactly = 1) { navigateToError(ErrorArgs.General(failure), any()) }
            verify(exactly = 0) { navigationRouter.back() }
            val state = assertNotNull(vm.state.value)
            assertEquals("Savings", state.field.value.text())
            assertTrue(state.saveButton.isEnabled)
        }

    @Test
    fun aDoubleTapSavesOnce() =
        runTest {
            val saved = CompletableDeferred<Unit>()
            coEvery { accountDataSource.renameAccount(any(), any()) } coAnswers { saved.await() }
            val vm = vm()
            collect(vm)
            type(vm, "Savings")

            val save = assertNotNull(vm.state.value).saveButton
            save.onClick()
            runCurrent()
            save.onClick()
            runCurrent()
            assertFalse(assertNotNull(vm.state.value).saveButton.isEnabled)
            saved.complete(Unit)
            runCurrent()

            coVerify(exactly = 1) { accountDataSource.renameAccount(any(), any()) }
            verify(exactly = 1) { navigationRouter.back() }
        }

    @Test
    fun backCancelsARunningSaveSoItNeverNavigatesAgain() =
        runTest {
            coEvery { accountDataSource.renameAccount(any(), any()) } coAnswers { awaitCancellation() }
            val vm = vm()
            collect(vm)
            type(vm, "Savings")
            assertNotNull(vm.state.value).saveButton.onClick()
            runCurrent()

            assertNotNull(vm.state.value).onBack()
            runCurrent()

            verify(exactly = 1) { navigationRouter.back() }
        }

    @Test
    fun removeOpensTheConfirmationSheetForThisAccount() =
        runTest {
            val vm = vm()
            collect(vm)

            assertNotNull(assertNotNull(vm.state.value).remove).button.onClick()

            verify(exactly = 1) { viewOnlyNavigator.forwardToRemoveAccount(EditAccountFixtures.viewOnlyKeyId) }
        }

    @Test
    fun theRemoveButtonKeepsItsCallbackAcrossPicks() =
        runTest {
            val vm = vm()
            collect(vm)
            val before = assertNotNull(assertNotNull(vm.state.value).remove).button

            pick(vm, Color(PICKED))

            assertEquals(before, assertNotNull(assertNotNull(vm.state.value).remove).button)
        }

    @Test
    fun aMissingAccountGoesBack() =
        runTest {
            accounts.value = listOf(EditAccountFixtures.zodl())

            val vm = vm()
            collect(vm)

            verify(exactly = 1) { navigationRouter.back() }
            assertNull(vm.state.value)
        }

    @Test
    fun aHardwareWalletIsNeverEdited() =
        runTest {
            accounts.value = listOf(EditAccountFixtures.keystone())

            val vm = vm(accountKeyId = EditAccountFixtures.hardwareUuid.toStorageKeyId())
            collect(vm)

            verify(exactly = 1) { navigationRouter.back() }
            assertNull(vm.state.value)
        }

    @Test
    fun aPickedColorShowsOnTheAvatarEnablesSaveAndIsSavedAlone() =
        runTest {
            val vm = vm()
            collect(vm)
            assertNull(assertNotNull(vm.state.value).avatarColor())

            pick(vm, Color(PICKED))

            val state = assertNotNull(vm.state.value)
            assertEquals(Color(PICKED), state.avatarColor())
            assertTrue(state.saveButton.isEnabled)

            state.saveButton.onClick()
            runCurrent()

            val saved = slot<WalletAccount>()
            coVerify(exactly = 1) { metadataRepository.setAccountColor(capture(saved), Color(PICKED)) }
            assertEquals(EditAccountFixtures.viewOnlyUuid, saved.captured.sdkAccount.accountUuid)
            coVerify(exactly = 0) { accountDataSource.renameAccount(any(), any()) }
            verify(exactly = 1) { navigationRouter.back() }
        }

    @Test
    fun saveStoresBothANewNameAndANewColor() =
        runTest {
            val vm = vm()
            collect(vm)

            type(vm, "Savings")
            pick(vm, Color(PICKED))
            assertNotNull(vm.state.value).saveButton.onClick()
            runCurrent()

            coVerify(exactly = 1) { accountDataSource.renameAccount(viewOnlyAccount(), "Savings") }
            coVerify(exactly = 1) { metadataRepository.setAccountColor(any(), Color(PICKED)) }
        }

    @Test
    fun anUnsavedColorShowsTheErrorAndKeepsThePickedColor() =
        runTest {
            coEvery { metadataRepository.setAccountColor(any(), any()) } returns false
            val vm = vm()
            collect(vm)
            pick(vm, Color(PICKED))

            assertNotNull(vm.state.value).saveButton.onClick()
            runCurrent()

            verifyColorNotSavedError()
            verify(exactly = 0) { navigationRouter.back() }
            val state = assertNotNull(vm.state.value)
            assertEquals(Color(PICKED), state.avatarColor())
            assertTrue(state.saveButton.isEnabled)
        }

    @Test
    fun theStoredColorShowsAndOpensThePicker() =
        runTest {
            storedColors.value = mapOf(EditAccountFixtures.viewOnlyUuid to Color(STORED))
            val vm = vm()
            collect(vm)

            assertEquals(Color(STORED), assertNotNull(vm.state.value).avatarColor())
            assertNotNull(assertNotNull(vm.state.value).avatar.editButton).onClick()
            runCurrent()

            assertEquals(Color(STORED), Color(assertNotNull(pickerRequests.single().initialArgb)))
        }

    @Test
    fun pickingTheStoredColorAgainLeavesSaveDisabled() =
        runTest {
            storedColors.value = mapOf(EditAccountFixtures.viewOnlyUuid to Color(STORED))
            val vm = vm()
            collect(vm)

            pick(vm, Color(STORED))

            assertFalse(assertNotNull(vm.state.value).saveButton.isEnabled)
        }

    @Test
    fun aPickerClosedWithoutAPickLeavesTheStoredAvatar() =
        runTest {
            val vm = vm()
            collect(vm)

            assertNotNull(assertNotNull(vm.state.value).avatar.editButton).onClick()
            runCurrent()
            navigateToColorPicker.onClosed(pickerRequests.single())
            runCurrent()

            assertNull(assertNotNull(vm.state.value).avatarColor())
            assertFalse(assertNotNull(vm.state.value).saveButton.isEnabled)
        }

    @Test
    fun theAvatarFollowsLivePicksAndKeepsTheLastOneWhenThePickerCloses() =
        runTest {
            storedColors.value = mapOf(EditAccountFixtures.viewOnlyUuid to Color(STORED))
            val vm = vm()
            collect(vm)
            assertNotNull(assertNotNull(vm.state.value).avatar.editButton).onClick()
            runCurrent()
            val request = pickerRequests.single()

            navigateToColorPicker.onPicked(Color(PICKED), request)
            runCurrent()
            assertEquals(Color(PICKED), assertNotNull(vm.state.value).avatarColor())
            assertTrue(assertNotNull(vm.state.value).saveButton.isEnabled)

            navigateToColorPicker.onPicked(Color(STORED), request)
            runCurrent()
            assertEquals(Color(STORED), assertNotNull(vm.state.value).avatarColor())
            assertFalse(assertNotNull(vm.state.value).saveButton.isEnabled)

            navigateToColorPicker.onPicked(Color(PICKED), request)
            navigateToColorPicker.onClosed(request)
            runCurrent()
            assertEquals(Color(PICKED), assertNotNull(vm.state.value).avatarColor())
            assertTrue(assertNotNull(vm.state.value).saveButton.isEnabled)
        }

    @Test
    fun aPickerClosedOffTheBackStackKeepsTheLastPickAndReopensOnIt() =
        runTest {
            val vm = vm()
            collect(vm)
            assertNotNull(assertNotNull(vm.state.value).avatar.editButton).onClick()
            runCurrent()
            navigateToColorPicker.onPicked(Color(PICKED), pickerRequests.single())
            navigateToColorPicker.onClosed(pickerRequests.single())
            runCurrent()

            assertEquals(Color(PICKED), assertNotNull(vm.state.value).avatarColor())
            assertNotNull(assertNotNull(vm.state.value).avatar.editButton).onClick()
            runCurrent()
            assertEquals(2, pickerRequests.size)
            assertEquals(Color(PICKED), Color(assertNotNull(pickerRequests.last().initialArgb)))
        }

    @Test
    fun pickingTheStoredColorPinsNothingSoALaterStoredColorShows() =
        runTest {
            storedColors.value = mapOf(EditAccountFixtures.viewOnlyUuid to Color(STORED))
            val vm = vm()
            collect(vm)

            pick(vm, Color(STORED))
            storedColors.value = mapOf(EditAccountFixtures.viewOnlyUuid to Color(PICKED))
            runCurrent()

            assertEquals(Color(PICKED), assertNotNull(vm.state.value).avatarColor())
            assertFalse(assertNotNull(vm.state.value).saveButton.isEnabled)
        }

    @Test
    fun theZodlNameIsShownDisabledWithoutCounterOrRemoval() =
        runTest {
            val vm = vm(accountKeyId = EditAccountFixtures.zodlKeyId)
            collect(vm)

            val state = assertNotNull(vm.state.value)
            assertEquals(stringRes(UiR.string.accounts_zashi), state.field.value)
            assertFalse(state.field.isEnabled)
            assertNull(state.counter)
            assertNull(state.remove)
            assertFalse(state.saveButton.isEnabled)
            assertEquals(imageRes(R.drawable.ic_item_zashi), state.avatar.image)
            assertNotNull(state.avatar.editButton)
        }

    @Test
    fun typingIntoTheZodlFieldNeverRenamesItAndSaveFollowsTheColorOnly() =
        runTest {
            val vm = vm(accountKeyId = EditAccountFixtures.zodlKeyId)
            collect(vm)

            type(vm, "Savings")
            val typed = assertNotNull(vm.state.value)
            assertFalse(typed.saveButton.isEnabled)
            assertEquals(stringRes(UiR.string.accounts_zashi), typed.field.value)

            pick(vm, Color(PICKED))
            assertTrue(assertNotNull(vm.state.value).saveButton.isEnabled)
            assertNotNull(vm.state.value).saveButton.onClick()
            runCurrent()

            val saved = slot<WalletAccount>()
            coVerify(exactly = 1) { metadataRepository.setAccountColor(capture(saved), Color(PICKED)) }
            assertEquals(EditAccountFixtures.zodlUuid, saved.captured.sdkAccount.accountUuid)
            coVerify(exactly = 0) { accountDataSource.renameAccount(any(), any()) }
            verify(exactly = 1) { navigationRouter.back() }
        }

    @Test
    fun aColoredZodlAvatarIsTheMarkOnTheColor() =
        runTest {
            storedColors.value = mapOf(EditAccountFixtures.zodlUuid to Color(STORED))
            val vm = vm(accountKeyId = EditAccountFixtures.zodlKeyId)
            collect(vm)

            assertEquals(zodlIcon(Color(STORED)), assertNotNull(vm.state.value).avatar.image)

            pick(vm, Color(PICKED))

            assertEquals(zodlIcon(Color(PICKED)), assertNotNull(vm.state.value).avatar.image)
        }

    @Test
    fun anUnsavedZodlColorShowsTheErrorAndKeepsThePickedColor() =
        runTest {
            coEvery { metadataRepository.setAccountColor(any(), any()) } returns false
            val vm = vm(accountKeyId = EditAccountFixtures.zodlKeyId)
            collect(vm)
            pick(vm, Color(PICKED))

            assertNotNull(vm.state.value).saveButton.onClick()
            runCurrent()

            verifyColorNotSavedError()
            verify(exactly = 0) { navigationRouter.back() }
            val state = assertNotNull(vm.state.value)
            assertEquals(zodlIcon(Color(PICKED)), state.avatar.image)
            assertTrue(state.saveButton.isEnabled)
        }

    @Test
    fun aRenameThatFailsNeverWritesTheColor() =
        runTest {
            val failure = IllegalStateException("database locked")
            coEvery { accountDataSource.renameAccount(any(), any()) } throws failure
            val vm = vm()
            collect(vm)
            type(vm, "Savings")
            pick(vm, Color(PICKED))

            assertNotNull(vm.state.value).saveButton.onClick()
            runCurrent()

            verify(exactly = 1) { navigateToError(ErrorArgs.General(failure), any()) }
            coVerify(exactly = 0) { metadataRepository.setAccountColor(any(), any()) }
            verify(exactly = 0) { navigationRouter.back() }
            assertEquals(Color(PICKED), assertNotNull(vm.state.value).avatarColor())
        }

    @Test
    fun aRetryAfterAnUnsavedColorSavesOnlyTheColor() =
        runTest {
            coEvery { metadataRepository.setAccountColor(any(), any()) } returns false
            val vm = vm()
            collect(vm)
            type(vm, "Savings")
            pick(vm, Color(PICKED))
            assertNotNull(vm.state.value).saveButton.onClick()
            runCurrent()
            verifyColorNotSavedError()
            accounts.value = listOf(EditAccountFixtures.zodl(), EditAccountFixtures.viewOnly(customName = "Savings"))
            runCurrent()

            coEvery { metadataRepository.setAccountColor(any(), any()) } returns true
            val retry = assertNotNull(vm.state.value).saveButton
            assertTrue(retry.isEnabled)
            retry.onClick()
            runCurrent()

            coVerify(exactly = 1) { accountDataSource.renameAccount(any(), any()) }
            coVerify(exactly = 2) { metadataRepository.setAccountColor(any(), Color(PICKED)) }
            verify(exactly = 1) { navigationRouter.back() }
        }

    @Test
    fun removeIsIgnoredWhileSaving() =
        runTest {
            val saved = CompletableDeferred<Unit>()
            coEvery { accountDataSource.renameAccount(any(), any()) } coAnswers { saved.await() }
            val vm = vm()
            collect(vm)
            type(vm, "Savings")
            val remove = assertNotNull(assertNotNull(vm.state.value).remove).button

            assertNotNull(vm.state.value).saveButton.onClick()
            runCurrent()
            assertFalse(assertNotNull(assertNotNull(vm.state.value).remove).button.isEnabled)
            remove.onClick()
            saved.complete(Unit)
            runCurrent()

            verify(exactly = 0) { viewOnlyNavigator.forwardToRemoveAccount(any()) }
        }

    @Test
    fun aSecondAvatarTapWhileThePickerIsOpenOpensNothing() =
        runTest {
            val vm = vm()
            collect(vm)
            val edit = assertNotNull(assertNotNull(vm.state.value).avatar.editButton)

            edit.onClick()
            runCurrent()
            edit.onClick()
            runCurrent()

            assertEquals(1, pickerRequests.size)
        }

    @Test
    fun anAccountRemovedWhileEditingGoesBackOnSave() =
        runTest {
            val vm = vm()
            collect(vm)
            type(vm, "Savings")
            val save = assertNotNull(vm.state.value).saveButton

            accounts.value = listOf(EditAccountFixtures.zodl())
            runCurrent()
            save.onClick()
            runCurrent()

            verify(exactly = 1) { navigationRouter.back() }
            coVerify(exactly = 0) { accountDataSource.renameAccount(any(), any()) }
            coVerify(exactly = 0) { metadataRepository.setAccountColor(any(), any()) }
            verify(exactly = 0) { navigateToError(any(), any()) }
        }

    /** Opens the picker, picks [color] and closes it. */
    private fun TestScope.pick(
        vm: EditAccountVM,
        color: Color,
    ) {
        assertNotNull(assertNotNull(vm.state.value).avatar.editButton).onClick()
        runCurrent()
        navigateToColorPicker.onPicked(color, pickerRequests.last())
        navigateToColorPicker.onClosed(pickerRequests.last())
        runCurrent()
    }

    private fun verifyColorNotSavedError() =
        verify(exactly = 1) {
            navigateToError(match { (it as? ErrorArgs.General)?.exception is AccountColorNotSavedException }, any())
        }

    private fun zodlIcon(color: Color) =
        ImageResource.Layered(background = color, foreground = ImageResource.ByDrawable(R.drawable.ic_item_zodl_glyph))

    private val EditAccountState.field get() = nameField.field

    private val EditAccountState.counter get() = nameField.counter

    private val EditAccountState.avatar get() = form.header.avatar

    private fun EditAccountState.avatarColor() =
        (avatar.image as ImageResource.Layered).background.takeIf { it.isSpecified }

    /** Matches the view-only account under edit, whichever snapshot of it the screen holds. */
    private fun MockKMatcherScope.viewOnlyAccount() =
        match<ViewOnlyAccount> { it.sdkAccount.accountUuid == EditAccountFixtures.viewOnlyUuid }

    private fun TestScope.type(
        vm: EditAccountVM,
        text: String,
    ) {
        assertNotNull(vm.state.value).field.onValueChange(text)
        runCurrent()
    }

    private fun vm(accountKeyId: String = EditAccountFixtures.viewOnlyKeyId) =
        EditAccountVM(
            args = EditAccountArgs(accountKeyId),
            getWalletAccounts = EditAccountFixtures.getWalletAccounts(accounts),
            renameViewOnlyAccount = RenameViewOnlyAccountUseCase(accountDataSource),
            observeAccountIcon = AccountIconFixtures.useCase(),
            metadataRepository = metadataRepository,
            navigateToColorPicker = navigateToColorPicker,
            navigateToError = navigateToError,
            navigationRouter = navigationRouter,
            viewOnlyNavigator = viewOnlyNavigator,
            viewOnlyContent = viewOnlyContent,
            isAccountNameTaken = isAccountNameTaken,
        )

    private fun TestScope.collect(vm: EditAccountVM) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        runCurrent()
    }

    private fun StringResource.text() = (this as StringResource.ByString).value

    private fun StringResource.args() = (this as StringResource.ByResource).args.toList()
}

private const val LOCK = "🔐"
private const val PICKED = 0xFF0088FF
private const val STORED = 0xFFBE38F3
private const val REMOVE_ICON = 7

/** Resource-backed, because mockk hands back a `@JvmInline` `StringResource.ByString` unboxed. */
private val REMOVE_INFO = stringRes(101)
private val REMOVE_BUTTON = stringRes(102)
