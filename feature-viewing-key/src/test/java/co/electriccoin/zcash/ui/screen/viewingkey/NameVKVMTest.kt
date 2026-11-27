package co.electriccoin.zcash.ui.screen.viewingkey

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.AccountNameLimit
import co.electriccoin.zcash.ui.common.model.LceError
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccountImportException
import co.electriccoin.zcash.ui.common.model.ViewOnlyPendingImportMissingException
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.PendingViewOnlyImport
import co.electriccoin.zcash.ui.common.repository.ViewOnlyPendingImportRepositoryImpl
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.ImportViewOnlyAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToColorPickerUseCase
import co.electriccoin.zcash.ui.common.usecase.ViewOnlyAccountFixtures
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.screen.colorpicker.ColorPickerArgs
import co.electriccoin.zcash.ui.screen.viewingkey.name.NameVKState
import co.electriccoin.zcash.ui.screen.viewingkey.name.NameVKVM
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The name is capped at twelve characters as it is typed, the counter follows it, and Continue needs
 * a name that is not blank. Naming is the last step: Continue stores the trimmed name and the picked
 * colour with the pending key and birthday and imports, showing its progress, once however often it
 * is tapped; without a pending key or birthday it returns to the root, and a failed import shows the
 * error, from which it is tried again. While it imports, the name field is disabled with its counter
 * still shown, and back, the name and the colour are ignored;
 * otherwise back returns to the birthday screen and keeps the pending key and birthday. The avatar's
 * pencil badge opens the colour picker; the avatar follows the picks live and keeps the last one
 * however the picker closes. A name another account shows, in any case, disables Continue and says
 * so under the field, never next to another name, and Continue checks the name again when tapped.
 * The keyboard's Done key continues only while Continue is enabled.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NameVKVMTest {
    private val navigationRouter = mockk<NavigationRouter>(relaxed = true)

    private val pickerRequests = mutableListOf<ColorPickerArgs>()

    private val navigateToColorPicker =
        NavigateToColorPickerUseCase(
            mockk(relaxed = true) {
                every { forward(*anyVararg()) } answers {
                    (args[0] as Array<*>).filterIsInstance<ColorPickerArgs>().forEach { pickerRequests.add(it) }
                }
            }
        )

    private val accounts =
        MutableStateFlow<List<WalletAccount>?>(
            listOf(ViewOnlyAccountFixtures.zodl(), ViewOnlyAccountFixtures.viewOnly(customName = "Keystone"))
        )

    private val isAccountNameTaken = ViewOnlyAccountFixtures.isAccountNameTaken(accounts)

    private val repository = ViewOnlyPendingImportRepositoryImpl().apply { set(pending()) }

    private var importAnswer: suspend () -> Unit = {}

    private val importViewOnlyAccount =
        mockk<ImportViewOnlyAccountUseCase> {
            coEvery { this@mockk.invoke() } coAnswers { importAnswer() }
        }

    private val errorStateMapper =
        ErrorMapperUseCase(
            sendEmail = mockk(relaxed = true),
            viewOnlyContent = mockk(relaxed = true),
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aLongerNameIsCutToTwelveCharacters() =
        runTest {
            val vm = vm()
            collect(vm)

            type(vm, "Zodl Savings 2026")

            assertEquals(
                "Zodl Savings",
                vm
                    .content()
                    .nameField.field.value
                    .text()
            )
            assertEquals(
                listOf(12, 12),
                assertNotNull(vm.content().nameField.counter)
                    .args()
            )
        }

    @Test
    fun emojiCountOnceAndAreNeverCutInHalf() =
        runTest {
            val vm = vm()
            collect(vm)

            type(vm, "Vault " + LOCK.repeat(8))

            assertEquals(
                "Vault " + LOCK.repeat(6),
                vm
                    .content()
                    .nameField.field.value
                    .text()
            )
            assertEquals(
                listOf(12, 12),
                assertNotNull(vm.content().nameField.counter)
                    .args()
            )

            type(vm, LOCK.repeat(3))

            assertEquals(
                listOf(3, 12),
                assertNotNull(vm.content().nameField.counter)
                    .args()
            )
        }

    @Test
    fun theCounterFollowsTheName() =
        runTest {
            val vm = vm()
            collect(vm)
            assertEquals(
                listOf(0, AccountNameLimit.MAX_LENGTH),
                assertNotNull(vm.content().nameField.counter)
                    .args()
            )

            type(vm, "Zodl Vault")

            assertEquals(
                listOf(10, AccountNameLimit.MAX_LENGTH),
                assertNotNull(vm.content().nameField.counter)
                    .args()
            )
        }

    @Test
    fun continueNeedsANameThatIsNotBlank() =
        runTest {
            val vm = vm()
            collect(vm)
            assertFalse(vm.content().continueButton.isEnabled)

            type(vm, "   ")
            assertFalse(vm.content().continueButton.isEnabled)
            vm
                .content()
                .continueButton
                .onClick()
            runCurrent()
            coVerify(exactly = 0) { importViewOnlyAccount.invoke() }

            type(vm, "Vault")
            assertTrue(vm.content().continueButton.isEnabled)
        }

    @Test
    fun aNameAnotherAccountShowsDisablesContinueAndShowsTheError() =
        runTest {
            val vm = vm()
            collect(vm)

            type(vm, " keystone ")

            assertFalse(vm.content().continueButton.isEnabled)
            assertEquals(
                R.string.accountName_duplicate,
                (
                    vm
                        .content()
                        .nameField.field.error as StringResource.ByResource
                ).resource
            )
            vm
                .content()
                .continueButton
                .onClick()
            runCurrent()
            coVerify(exactly = 0) { importViewOnlyAccount.invoke() }
            assertEquals(pending(), repository.get())
        }

    @Test
    fun aUniqueNameEnablesContinueWithoutAnError() =
        runTest {
            val vm = vm()
            collect(vm)

            type(vm, "ZODL")
            assertFalse(vm.content().continueButton.isEnabled)

            type(vm, "Zodl 2")

            assertTrue(vm.content().continueButton.isEnabled)
            assertNull(
                vm
                    .content()
                    .nameField.field.error
            )
        }

    @Test
    fun theDuplicateErrorAlwaysBelongsToTheNameShown() =
        runTest {
            val vm = vm()
            val states = mutableListOf<NameVKState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.state.collect { state -> state.content?.let { states.add(it) } }
            }
            runCurrent()

            listOf("Keystone", "Savings", "zodl", "Vault").forEach { type(vm, it) }

            states.forEach { state ->
                val shown =
                    state.nameField.field.value
                        .text()
                val isDuplicate = shown.trim().lowercase() in setOf("keystone", "zodl")
                assertEquals(isDuplicate, state.nameField.field.error != null, shown)
            }
        }

    @Test
    fun continueRechecksTheNameAgainstTheAccountsWhenTapped() =
        runTest {
            val vm = vm()
            collect(vm)
            type(vm, "Savings")
            val continueButton = vm.content().continueButton
            assertTrue(continueButton.isEnabled)

            accounts.value = listOf(ViewOnlyAccountFixtures.viewOnly(customName = "Savings"))
            continueButton.onClick()
            runCurrent()

            coVerify(exactly = 0) { importViewOnlyAccount.invoke() }
            assertEquals(pending(), repository.get())
        }

    @Test
    fun doneOnTheKeyboardDoesNothingForABlankOrDuplicateName() =
        runTest {
            val vm = vm()
            collect(vm)

            type(vm, "   ")
            vm
                .content()
                .nameField
                .onDone()
            runCurrent()
            type(vm, "Keystone")
            vm
                .content()
                .nameField
                .onDone()
            runCurrent()

            coVerify(exactly = 0) { importViewOnlyAccount.invoke() }
            assertEquals(pending(), repository.get())
        }

    @Test
    fun doneOnTheKeyboardContinuesWhileContinueIsEnabled() =
        runTest {
            val vm = vm()
            collect(vm)
            type(vm, "Vault")

            vm
                .content()
                .nameField
                .onDone()
            runCurrent()

            assertEquals(pending(name = "Vault"), repository.get())
            coVerify(exactly = 1) { importViewOnlyAccount.invoke() }
        }

    @Test
    fun continueImportsWithTheTrimmedNameTheColorAndTheBirthdayAndShowsItsProgress() =
        runTest {
            val importing = CompletableDeferred<Unit>()
            var imported: PendingViewOnlyImport? = null
            importAnswer = {
                imported = repository.get()
                importing.await()
            }
            val vm = vm()
            collect(vm)
            pickColor(vm, Color(PICKED))
            type(vm, " Vault ")

            vm
                .content()
                .continueButton
                .onClick()
            runCurrent()

            assertEquals(pending(name = "Vault", color = Color(PICKED)), imported)
            coVerify(exactly = 1) { importViewOnlyAccount.invoke() }
            assertTrue(vm.content().continueButton.isLoading)
            assertFalse(vm.content().continueButton.isEnabled)

            importing.complete(Unit)
            runCurrent()

            assertFalse(vm.content().continueButton.isLoading)
            verify(exactly = 0) { navigationRouter.backToRoot() }
        }

    @Test
    fun aDoubleTapImportsOnce() =
        runTest {
            val importing = CompletableDeferred<Unit>()
            importAnswer = { importing.await() }
            val vm = vm()
            collect(vm)
            type(vm, "Vault")
            val continueButton = vm.content().continueButton

            continueButton.onClick()
            continueButton.onClick()
            runCurrent()
            continueButton.onClick()
            vm
                .content()
                .continueButton
                .onClick()
            runCurrent()

            coVerify(exactly = 1) { importViewOnlyAccount.invoke() }
            importing.complete(Unit)
        }

    @Test
    fun withoutAPendingKeyAtContinueTheFlowReturnsToTheRoot() =
        runTest {
            val vm = vm()
            collect(vm)
            type(vm, "Vault")
            repository.clear()

            vm
                .content()
                .continueButton
                .onClick()
            runCurrent()

            verify(exactly = 1) { navigationRouter.backToRoot() }
            coVerify(exactly = 0) { importViewOnlyAccount.invoke() }
        }

    @Test
    fun withoutABirthdayAtContinueTheFlowReturnsToTheRoot() =
        runTest {
            repository.set(PendingViewOnlyImport(ufvk = UFVK, name = ""))
            val vm = vm()
            collect(vm)
            type(vm, "Vault")

            vm
                .content()
                .continueButton
                .onClick()
            runCurrent()

            verify(exactly = 1) { navigationRouter.backToRoot() }
            coVerify(exactly = 0) { importViewOnlyAccount.invoke() }
            assertEquals(PendingViewOnlyImport(ufvk = UFVK, name = ""), repository.get())
        }

    @Test
    fun aPendingKeyGoneDuringTheImportReturnsToTheRoot() =
        runTest {
            importAnswer = { throw ViewOnlyPendingImportMissingException() }
            val vm = vm()
            collect(vm)
            type(vm, "Vault")

            vm
                .content()
                .continueButton
                .onClick()
            runCurrent()

            verify(exactly = 1) { navigationRouter.backToRoot() }
            assertNull(vm.state.value.error)
        }

    @Test
    fun aFailedImportShowsTheErrorAndRetryImportsAgain() =
        runTest {
            var calls = 0
            importAnswer = {
                calls++
                if (calls == 1) throw ViewOnlyAccountImportException()
            }
            val vm = vm()
            collect(vm)
            type(vm, "Vault")

            vm
                .content()
                .continueButton
                .onClick()
            runCurrent()

            val error = assertIs<LceError.BottomSheet>(vm.state.value.error)
            assertFalse(vm.content().continueButton.isLoading)
            assertTrue(vm.content().continueButton.isEnabled)

            error.state.primaryAction
                .onClick()
            runCurrent()

            coVerify(exactly = 2) { importViewOnlyAccount.invoke() }
            assertNull(vm.state.value.error)
            assertEquals(pending(name = "Vault"), repository.get())
        }

    @Test
    fun backWhileImportingAndEditsWhileImportingAreIgnored() =
        runTest {
            val importing = CompletableDeferred<Unit>()
            importAnswer = { importing.await() }
            val vm = vm()
            collect(vm)
            type(vm, "Vault")
            vm
                .content()
                .continueButton
                .onClick()
            runCurrent()

            vm.content().onBack()
            type(vm, "Savings")
            openPicker(vm)

            verify(exactly = 0) { navigationRouter.back() }
            assertEquals(
                "Vault",
                vm
                    .content()
                    .nameField.field.value
                    .text()
            )
            assertTrue(pickerRequests.isEmpty())
            importing.complete(Unit)
        }

    @Test
    fun theNameFieldIsDisabledWhileImportingAndEnabledAgainAfterAFailedImport() =
        runTest {
            val importing = CompletableDeferred<Unit>()
            importAnswer = {
                importing.await()
                throw ViewOnlyAccountImportException()
            }
            val vm = vm()
            collect(vm)
            type(vm, "Vault")
            assertTrue(
                vm
                    .content()
                    .nameField.field.isEnabled
            )

            vm
                .content()
                .continueButton
                .onClick()
            runCurrent()

            assertFalse(
                vm
                    .content()
                    .nameField.field.isEnabled
            )
            assertEquals(
                listOf(5, AccountNameLimit.MAX_LENGTH),
                assertNotNull(vm.content().nameField.counter)
                    .args()
            )

            importing.complete(Unit)
            runCurrent()

            assertIs<LceError.BottomSheet>(vm.state.value.error)
            assertTrue(
                vm
                    .content()
                    .nameField.field.isEnabled
            )
        }

    @Test
    fun backReturnsToTheBirthdayAndKeepsThePendingKeyAndBirthday() =
        runTest {
            val vm = vm()
            collect(vm)
            type(vm, "Vault")

            vm.content().onBack()

            assertEquals(pending(), repository.get())
            verify(exactly = 1) { navigationRouter.back() }
            verify(exactly = 0) { navigationRouter.backToRoot() }
            coVerify(exactly = 0) { importViewOnlyAccount.invoke() }
        }

    @Test
    fun withoutAPendingKeyTheFlowReturnsToTheRoot() =
        runTest {
            repository.clear()

            vm()

            verify(exactly = 1) { navigationRouter.backToRoot() }
        }

    @Test
    fun aPickedColorShowsOnTheAvatarAndContinueKeepsItWithThePendingImport() =
        runTest {
            val vm = vm()
            collect(vm)
            assertNull(vm.content().avatarColor())

            openPicker(vm)
            val request = pickerRequests.single()
            assertNull(request.initialArgb)
            navigateToColorPicker.onPicked(Color(PICKED), request)
            navigateToColorPicker.onClosed(request)
            runCurrent()

            assertEquals(Color(PICKED), vm.content().avatarColor())
            assertEquals(pending(), repository.get())

            type(vm, "Savings")
            vm
                .content()
                .continueButton
                .onClick()
            runCurrent()

            assertEquals(pending(name = "Savings", color = Color(PICKED)), repository.get())
        }

    @Test
    fun theColorOfThePendingImportShowsWhenTheScreenOpensAgain() =
        runTest {
            repository.set(pending(name = "Savings", color = Color(PICKED)))
            val vm = vm()
            collect(vm)

            assertEquals(Color(PICKED), vm.content().avatarColor())
        }

    @Test
    fun aPickerClosedWithoutAPickKeepsTheColorAndReopensWithIt() =
        runTest {
            val vm = vm()
            collect(vm)
            openPicker(vm)
            navigateToColorPicker.onPicked(Color(PICKED), pickerRequests.last())
            navigateToColorPicker.onClosed(pickerRequests.last())
            runCurrent()

            openPicker(vm)
            val second = pickerRequests.last()
            assertEquals(Color(PICKED), Color(assertNotNull(second.initialArgb)))
            navigateToColorPicker.onClosed(second)
            runCurrent()

            assertEquals(Color(PICKED), vm.content().avatarColor())
        }

    @Test
    fun theAvatarFollowsLivePicksAndKeepsTheLastOneWhenThePickerCloses() =
        runTest {
            val vm = vm()
            collect(vm)
            openPicker(vm)
            val request = pickerRequests.single()

            navigateToColorPicker.onPicked(Color(OTHER), request)
            runCurrent()
            assertEquals(Color(OTHER), vm.content().avatarColor())

            navigateToColorPicker.onPicked(Color(PICKED), request)
            navigateToColorPicker.onClosed(request)
            runCurrent()
            assertEquals(Color(PICKED), vm.content().avatarColor())

            openPicker(vm)
            assertEquals(2, pickerRequests.size)
        }

    @Test
    fun aSecondTapWhileThePickerIsOpenOpensNothing() =
        runTest {
            val vm = vm()
            collect(vm)

            openPicker(vm)
            openPicker(vm)

            assertEquals(1, pickerRequests.size)
        }

    private fun NameVKState.avatarColor() =
        (form.header.avatar.image as ImageResource.Layered).background.takeIf { it.isSpecified }

    private fun TestScope.openPicker(vm: NameVKVM) {
        assertNotNull(
            vm
                .content()
                .form.header.avatar.editButton
        ).onClick()
        runCurrent()
    }

    private fun TestScope.pickColor(
        vm: NameVKVM,
        color: Color,
    ) {
        openPicker(vm)
        val request = pickerRequests.last()
        navigateToColorPicker.onPicked(color, request)
        navigateToColorPicker.onClosed(request)
        runCurrent()
    }

    private fun NameVKVM.content() = assertNotNull(state.value.content)

    private fun pending(
        name: String = "",
        color: Color? = null,
    ) = PendingViewOnlyImport(ufvk = UFVK, name = name, color = color, birthday = BIRTHDAY)

    private fun TestScope.type(
        vm: NameVKVM,
        text: String,
    ) {
        vm
            .content()
            .nameField.field
            .onValueChange(text)
        runCurrent()
    }

    private fun vm() =
        NameVKVM(
            pendingImportRepository = repository,
            navigationRouter = navigationRouter,
            navigateToColorPicker = navigateToColorPicker,
            isAccountNameTaken = isAccountNameTaken,
            importViewOnlyAccount = importViewOnlyAccount,
            errorStateMapper = errorStateMapper,
        )

    private fun TestScope.collect(vm: NameVKVM) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        runCurrent()
    }

    private fun StringResource.text() = (this as StringResource.ByString).value

    private fun StringResource.args() = (this as StringResource.ByResource).args.toList()

    private companion object {
        const val UFVK = "uview1namefixture"
        const val PICKED = 0x80BE38F3
        const val OTHER = 0xFF0088FF
        val BIRTHDAY = BlockHeight.new(2_500_000L)
    }
}

private const val LOCK = "\uD83D\uDD10"
