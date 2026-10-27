package co.electriccoin.zcash.ui.screen.connecthw

import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.usecase.CreateHWWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyContent
import co.electriccoin.zcash.ui.design.component.NumberTextFieldInnerState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.fixture.WalletFixture
import co.electriccoin.zcash.ui.screen.connecthw.date.HWDateArgs
import co.electriccoin.zcash.ui.screen.connecthw.date.HWDateVM
import co.electriccoin.zcash.ui.screen.connecthw.estimation.HWEstimationArgs
import co.electriccoin.zcash.ui.screen.connecthw.estimation.HWEstimationVM
import co.electriccoin.zcash.ui.screen.connecthw.height.HWHeightArgs
import co.electriccoin.zcash.ui.screen.connecthw.height.HWHeightVM
import co.electriccoin.zcash.ui.screen.connecthw.neworactive.HWNewOrActiveArgs
import co.electriccoin.zcash.ui.screen.connecthw.neworactive.HWNewOrActiveVM
import co.electriccoin.zcash.ui.screen.heightinfo.HeightInfoArgs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The birthday screens are shared by Keystone, Ledger and the view-only import, so every case runs
 * against all three enrollments: the flow, the wiring and the validation are asserted once, and
 * only the branding each enrollment resolves to is asserted per enrollment. The new-or-active
 * question is asked by the hardware wallets only, so its cases run against those two.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HWEnrollmentVMTest {
    private val enrollments =
        listOf(
            HWWalletEnrollment.Keystone(ur = "ur:zcash-accounts/fixture"),
            HWWalletEnrollment.Ledger,
            HWWalletEnrollment.ViewOnly,
        )

    private val hardwareEnrollments =
        listOf(
            HWWalletEnrollment.Keystone(ur = "ur:zcash-accounts/fixture"),
            HWWalletEnrollment.Ledger,
        )

    private val viewOnlyBranding =
        HWWalletBranding(
            logo = null,
            newOrActive = null,
            enterBlockHeightManually = stringRes("Enter block height"),
            connect = stringRes("Continue"),
            enterManuallyTestTag = "VIEW_ONLY_ENTER_MANUALLY",
            connectTestTag = "VIEW_ONLY_CONTINUE",
            blockHeightFieldTestTag = "VIEW_ONLY_HEIGHT_FIELD",
            dateTitle = stringRes("First Account Transaction"),
            dateSubtitle = stringRes("Choose the approximate date of this account's first transaction."),
        )

    private val viewOnlyContent =
        mockk<ViewOnlyContent> { every { enrollmentBranding } returns viewOnlyBranding }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theVendorNeutralEnrollmentTextsAreOneStringForBothVendors() {
        val keystone = brandingOf(HWWalletEnrollment.Keystone(ur = "ur"), viewOnlyContent)
        val ledger = brandingOf(HWWalletEnrollment.Ledger, viewOnlyContent)
        val keystoneNewOrActive = assertNotNull(keystone.newOrActive)
        val ledgerNewOrActive = assertNotNull(ledger.newOrActive)

        assertEquals(R.string.keystone_addHWWallet_deviceQuestion, keystoneNewOrActive.deviceQuestion.resourceId())
        assertEquals(R.string.keystone_addHWWallet_deviceDesc, keystoneNewOrActive.deviceDescription.resourceId())
        assertEquals(R.string.keystone_addHWWallet_connectNew, keystoneNewOrActive.connectNewDevice.resourceId())
        assertEquals(
            R.string.keystone_addHWWallet_connectActive,
            keystoneNewOrActive.connectActiveDevice.resourceId()
        )
        assertEquals(R.string.keystone_addHWWallet_connect, keystone.connect.resourceId())
        assertEquals(R.string.keystone_addHWWallet_enterManually, keystone.enterBlockHeightManually.resourceId())

        assertEquals(keystoneNewOrActive.deviceQuestion, ledgerNewOrActive.deviceQuestion)
        assertEquals(keystoneNewOrActive.deviceDescription, ledgerNewOrActive.deviceDescription)
        assertEquals(keystoneNewOrActive.connectNewDevice, ledgerNewOrActive.connectNewDevice)
        assertEquals(keystoneNewOrActive.connectActiveDevice, ledgerNewOrActive.connectActiveDevice)
        assertEquals(keystone.enterBlockHeightManually, ledger.enterBlockHeightManually)
        assertEquals(keystone.connect, ledger.connect)
    }

    @Test
    fun theLogoAndTestTagsAreWhatActuallyDifferPerVendor() {
        val keystone = brandingOf(HWWalletEnrollment.Keystone(ur = "ur"), viewOnlyContent)
        val ledger = brandingOf(HWWalletEnrollment.Ledger, viewOnlyContent)

        assertEquals(HWWalletEnrollmentTag.KEYSTONE_NEW_DEVICE, keystone.newOrActive?.newDeviceTestTag)
        assertEquals(HWWalletEnrollmentTag.KEYSTONE_ACTIVE_DEVICE, keystone.newOrActive?.activeDeviceTestTag)
        assertEquals(HWWalletEnrollmentTag.KEYSTONE_CONNECT_BTN, keystone.connectTestTag)
        assertEquals(HWWalletEnrollmentTag.LEDGER_NEW_DEVICE, ledger.newOrActive?.newDeviceTestTag)
        assertEquals(HWWalletEnrollmentTag.LEDGER_ACTIVE_DEVICE, ledger.newOrActive?.activeDeviceTestTag)
        assertEquals(HWWalletEnrollmentTag.LEDGER_CONNECT_BTN, ledger.connectTestTag)
        assertNotEquals(keystone.logo, ledger.logo)
    }

    @Test
    fun aViewOnlyImportTakesItsBrandingFromTheViewOnlyFeature() {
        assertSame(viewOnlyBranding, brandingOf(HWWalletEnrollment.ViewOnly, viewOnlyContent))
    }

    @Test
    fun theEnrollmentSurvivesTheRouteEncoding() {
        enrollments.forEach { enrollment ->
            val encoded = Json.encodeToString<HWWalletEnrollment>(enrollment)

            assertEquals(enrollment, HWWalletEnrollmentNavType.parseValue(encoded))
        }
    }

    @Test
    fun aViewOnlyImportNeverReachesTheNewOrActiveQuestion() =
        runTest {
            val createAccount = readyUseCase()
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm = newOrActiveVM(HWWalletEnrollment.ViewOnly, createAccount, navigationRouter)
            collect { vm.state.collect { } }

            assertNull(vm.state.value.content)
            verify(exactly = 1) { navigationRouter.backToRoot() }
            coVerify(exactly = 0) { createAccount.invoke(any(), any()) }
        }

    @Test
    fun newDeviceImportsFromTheTipForBothVendors() =
        runTest {
            hardwareEnrollments.forEach { enrollment ->
                val createAccount = readyUseCase()
                val vm = newOrActiveVM(enrollment, createAccount)
                collect { vm.state.collect { } }

                val state = assertNotNull(vm.state.value.content)
                assertEquals(brandingOf(enrollment, viewOnlyContent).logo, state.logo)
                state.newDevice.onClick()
                runCurrent()

                coVerify(exactly = 1) { createAccount.invoke(enrollment, null) }
            }
        }

    @Test
    fun activeDeviceGoesToTheBirthdayQuestionForBothVendors() =
        runTest {
            hardwareEnrollments.forEach { enrollment ->
                val navigationRouter = mockk<NavigationRouter>(relaxed = true)
                val vm = newOrActiveVM(enrollment, readyUseCase(), navigationRouter)
                collect { vm.state.collect { } }

                assertNotNull(vm.state.value.content).activeDevice.onClick()

                verify(exactly = 1) { navigationRouter.forward(HWDateArgs(enrollment)) }
            }
        }

    @Test
    fun anEnrollmentThatLostWhatItNeedsReturnsToTheRootFromEveryScreen() =
        runTest {
            hardwareEnrollments.forEach { enrollment ->
                val createAccount =
                    mockk<CreateHWWalletAccountUseCase>(relaxed = true) {
                        every { isReady(any()) } returns false
                    }
                val navigationRouter = mockk<NavigationRouter>(relaxed = true)

                newOrActiveVM(enrollment, createAccount, navigationRouter)

                verify(exactly = 1) { navigationRouter.backToRoot() }
            }
            enrollments.forEach { enrollment ->
                val createAccount =
                    mockk<CreateHWWalletAccountUseCase>(relaxed = true) {
                        every { isReady(any()) } returns false
                    }
                val routers = List(3) { mockk<NavigationRouter>(relaxed = true) }

                dateVM(enrollment, createAccount, routers[0])
                estimationVM(enrollment, createAccount, routers[1])
                heightVM(enrollment, createAccount, routers[2])

                routers.forEach { verify(exactly = 1) { it.backToRoot() } }
            }
        }

    @Test
    fun theDateScreenOffersTheManualHeightRouteForBothVendors() =
        runTest {
            enrollments.forEach { enrollment ->
                val navigationRouter = mockk<NavigationRouter>(relaxed = true)
                val vm = dateVM(enrollment, readyUseCase(), navigationRouter)
                collect { vm.state.collect { } }

                val state = assertNotNull(vm.state.value.content)
                assertEquals(brandingOf(enrollment, viewOnlyContent).enterManuallyTestTag, state.secondaryButtonTestTag)
                assertNotNull(state.secondaryButton).onClick()
                state.dialogButton?.onClick?.invoke()

                verify(exactly = 1) { navigationRouter.forward(HWHeightArgs(enrollment)) }
                verify(exactly = 1) { navigationRouter.forward(HeightInfoArgs) }
            }
        }

    @Test
    fun theBirthdayScreensShowTheVendorLogoForAHardwareWalletAndNoLogoForAViewOnlyImport() =
        runTest {
            enrollments.forEach { enrollment ->
                val date = dateVM(enrollment, readyUseCase())
                val estimation = estimationVM(enrollment, readyUseCase())
                val height = heightVM(enrollment, readyUseCase())
                collect { height.state.collect { } }
                val expected =
                    when (enrollment) {
                        is HWWalletEnrollment.Keystone -> co.electriccoin.zcash.ui.design.R.drawable.image_keystone
                        HWWalletEnrollment.Ledger -> R.drawable.ic_ledger_wordmark
                        HWWalletEnrollment.ViewOnly -> null
                    }

                assertEquals(expected, assertNotNull(date.state.value.content).logo, "$enrollment date")
                assertEquals(expected, assertNotNull(estimation.state.value.content).logo, "$enrollment estimation")
                assertEquals(expected, assertNotNull(height.state.value.content).logo, "$enrollment height")
            }
        }

    @Test
    fun theDateScreenTakesItsHeadingFromTheBrandingWhenItSetsOne() {
        hardwareEnrollments.forEach { enrollment ->
            val state = assertNotNull(dateVM(enrollment, readyUseCase()).state.value.content)

            assertEquals(R.string.restoreWallet_birthday_estimateDate_title, state.subtitle.resourceId())
            assertEquals(R.string.firstWalletTransactionSubtitleHWWallet, state.message.resourceId())
        }

        val viewOnly = assertNotNull(dateVM(HWWalletEnrollment.ViewOnly, readyUseCase()).state.value.content)

        assertEquals(viewOnlyBranding.dateTitle, viewOnly.subtitle)
        assertEquals(viewOnlyBranding.dateSubtitle, viewOnly.message)
        assertEquals(R.string.general_next, viewOnly.primaryButton.text.resourceId())
    }

    @Test
    fun theDateScreenStartsOnSaplingActivationForAHardwareWalletAndForAViewOnlyImport() {
        hardwareEnrollments.forEach { enrollment ->
            val selection = assertNotNull(dateVM(enrollment, readyUseCase()).state.value.content).selection

            assertEquals(WalletFixture.SAPLING_ACTIVATION_YEAR_MONTH, selection)
        }

        val selection = assertNotNull(dateVM(HWWalletEnrollment.ViewOnly, readyUseCase()).state.value.content).selection

        assertEquals(WalletFixture.SAPLING_ACTIVATION_YEAR_MONTH, selection)
    }

    @Test
    fun theEstimationScreenImportsAtTheEstimatedHeightForBothVendors() =
        runTest {
            enrollments.forEach { enrollment ->
                val createAccount = readyUseCase()
                val vm = estimationVM(enrollment, createAccount)
                collect { vm.state.collect { } }

                val state = assertNotNull(vm.state.value.content)
                assertEquals(brandingOf(enrollment, viewOnlyContent).connect, state.primaryButton.text)
                state.primaryButton.onClick()
                runCurrent()

                coVerify(exactly = 1) { createAccount.invoke(enrollment, BlockHeight.new(ESTIMATED_HEIGHT)) }
            }
        }

    @Test
    fun theHeightScreenRefusesAnythingBelowSaplingActivationForBothVendors() =
        runTest {
            enrollments.forEach { enrollment ->
                val createAccount = readyUseCase()
                val vm = heightVM(enrollment, createAccount)
                collect { vm.state.collect { } }

                assertFalse(assertNotNull(vm.state.value.content).primaryButton.isEnabled)

                assertNotNull(vm.state.value.content).blockHeight.onValueChange(heightText("1"))
                runCurrent()
                assertFalse(assertNotNull(vm.state.value.content).primaryButton.isEnabled)

                assertNotNull(vm.state.value.content).blockHeight.onValueChange(heightText(VALID_HEIGHT))
                runCurrent()
                val valid = assertNotNull(vm.state.value.content)
                assertTrue(valid.primaryButton.isEnabled)
                val branding = brandingOf(enrollment, viewOnlyContent)
                assertEquals(branding.connectTestTag, valid.primaryButtonTestTag)
                assertEquals(branding.blockHeightFieldTestTag, valid.blockHeightFieldTestTag)

                valid.primaryButton.onClick()
                runCurrent()

                coVerify(exactly = 1) {
                    createAccount.invoke(enrollment, BlockHeight.new(VALID_HEIGHT.toLong()))
                }
            }
        }

    @Test
    fun aSecondTapOnNewDeviceWhileImportingStartsNoSecondImport() =
        runTest {
            hardwareEnrollments.forEach { enrollment ->
                val importing = CompletableDeferred<Unit>()
                val createAccount = suspendedUseCase(importing)
                val vm = newOrActiveVM(enrollment, createAccount)
                collect { vm.state.collect { } }

                assertNotNull(vm.state.value.content).newDevice.onClick()
                runCurrent()
                val loading = assertNotNull(vm.state.value.content)
                assertFalse(loading.newDevice.isEnabled)
                loading.newDevice.onClick()
                runCurrent()

                coVerify(exactly = 1) { createAccount.invoke(enrollment, null) }
                importing.complete(Unit)
            }
        }

    @Test
    fun aSecondTapOnImportWhileImportingStartsNoSecondImport() =
        runTest {
            enrollments.forEach { enrollment ->
                val importing = CompletableDeferred<Unit>()
                val createAccount = suspendedUseCase(importing)
                val vm = estimationVM(enrollment, createAccount)
                collect { vm.state.collect { } }

                assertNotNull(vm.state.value.content).primaryButton.onClick()
                runCurrent()
                val loading = assertNotNull(vm.state.value.content)
                assertFalse(loading.primaryButton.isEnabled)
                loading.primaryButton.onClick()
                runCurrent()

                coVerify(exactly = 1) { createAccount.invoke(enrollment, BlockHeight.new(ESTIMATED_HEIGHT)) }
                importing.complete(Unit)
            }
        }

    /**
     * Both taps go through the state drawn before the first one, as two taps landing in one frame
     * do, so the disabled button of the next state cannot be what stops the second import.
     */
    @Test
    fun aSecondTapOnImportAtATypedHeightStartsNoSecondImport() =
        runTest {
            enrollments.forEach { enrollment ->
                val importing = CompletableDeferred<Unit>()
                val createAccount = suspendedUseCase(importing)
                val vm = heightVM(enrollment, createAccount)
                collect { vm.state.collect { } }

                assertNotNull(vm.state.value.content).blockHeight.onValueChange(heightText(VALID_HEIGHT))
                runCurrent()
                val ready = assertNotNull(vm.state.value.content)
                ready.primaryButton.onClick()
                runCurrent()
                ready.primaryButton.onClick()
                runCurrent()

                coVerify(exactly = 1) { createAccount.invoke(enrollment, BlockHeight.new(VALID_HEIGHT.toLong())) }
                assertTrue(assertNotNull(vm.state.value.content).primaryButton.isLoading)
                importing.complete(Unit)
            }
        }

    private fun suspendedUseCase(importing: CompletableDeferred<Unit>) =
        mockk<CreateHWWalletAccountUseCase>(relaxed = true) {
            every { isReady(any()) } returns true
            coEvery { this@mockk.invoke(any(), any()) } coAnswers { importing.await() }
        }

    private fun heightText(amount: String) =
        NumberTextFieldInnerState.fromAmount(amount.toBigDecimal())

    private fun readyUseCase() =
        mockk<CreateHWWalletAccountUseCase>(relaxed = true) {
            every { isReady(any()) } returns true
            coEvery { this@mockk.invoke(any(), any()) } returns Unit
        }

    private fun newOrActiveVM(
        enrollment: HWWalletEnrollment,
        createAccount: CreateHWWalletAccountUseCase,
        navigationRouter: NavigationRouter = mockk(relaxed = true),
    ) = HWNewOrActiveVM(
        args = HWNewOrActiveArgs(enrollment),
        createHWWalletAccount = createAccount,
        navigationRouter = navigationRouter,
        errorStateMapper = errorStateMapper(),
        viewOnlyContent = viewOnlyContent,
    )

    private fun dateVM(
        enrollment: HWWalletEnrollment,
        createAccount: CreateHWWalletAccountUseCase,
        navigationRouter: NavigationRouter = mockk(relaxed = true),
    ) = HWDateVM(
        args = HWDateArgs(enrollment),
        createHWWalletAccount = createAccount,
        navigationRouter = navigationRouter,
        application = mockk(relaxed = true),
        errorStateMapper = errorStateMapper(),
        viewOnlyContent = viewOnlyContent,
    )

    private fun estimationVM(
        enrollment: HWWalletEnrollment,
        createAccount: CreateHWWalletAccountUseCase,
        navigationRouter: NavigationRouter = mockk(relaxed = true),
    ) = HWEstimationVM(
        args = HWEstimationArgs(enrollment, ESTIMATED_HEIGHT),
        createHWWalletAccount = createAccount,
        navigationRouter = navigationRouter,
        errorStateMapper = errorStateMapper(),
        viewOnlyContent = viewOnlyContent,
    )

    private fun heightVM(
        enrollment: HWWalletEnrollment,
        createAccount: CreateHWWalletAccountUseCase,
        navigationRouter: NavigationRouter = mockk(relaxed = true),
    ) = HWHeightVM(
        args = HWHeightArgs(enrollment),
        createHWWalletAccount = createAccount,
        navigationRouter = navigationRouter,
        errorStateMapper = errorStateMapper(),
        viewOnlyContent = viewOnlyContent,
    )

    private fun errorStateMapper() =
        ErrorMapperUseCase(
            sendEmail = mockk(relaxed = true),
            viewOnlyContent = mockk(relaxed = true),
        )

    private fun TestScope.collect(block: suspend CoroutineScope.() -> Unit) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { block() }
    }

    private fun StringResource.resourceId(): Int = (this as StringResource.ByResource).resource
}

private const val ESTIMATED_HEIGHT = 2_500_000L

private const val VALID_HEIGHT = "2500000"
