package co.electriccoin.zcash.ui.screen.connecthw

import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.usecase.CreateHWWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.design.component.NumberTextFieldInnerState
import co.electriccoin.zcash.ui.design.util.StringResource
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
import kotlin.test.assertTrue

/**
 * The four enrollment screens are shared by Keystone and Ledger, so every case runs against both
 * enrollments: the flow, the wiring and the validation are asserted once, and only the branding
 * each vendor resolves to is asserted per vendor.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HWEnrollmentVMTest {
    private val enrollments =
        listOf(
            HWWalletEnrollment.Keystone(ur = "ur:zcash-accounts/fixture"),
            HWWalletEnrollment.Ledger,
        )

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
        val keystone = brandingOf(HWWalletEnrollment.Keystone(ur = "ur"))
        val ledger = brandingOf(HWWalletEnrollment.Ledger)

        assertEquals(R.string.keystone_addHWWallet_deviceQuestion, keystone.deviceQuestion.resourceId())
        assertEquals(R.string.keystone_addHWWallet_connectNew, keystone.connectNewDevice.resourceId())
        assertEquals(R.string.keystone_addHWWallet_connectActive, keystone.connectActiveDevice.resourceId())
        assertEquals(R.string.keystone_addHWWallet_connect, keystone.connect.resourceId())
        assertEquals(R.string.keystone_addHWWallet_enterManually, keystone.enterBlockHeightManually.resourceId())

        assertEquals(keystone.deviceQuestion, ledger.deviceQuestion)
        assertEquals(keystone.deviceDescription, ledger.deviceDescription)
        assertEquals(keystone.connectNewDevice, ledger.connectNewDevice)
        assertEquals(keystone.connectActiveDevice, ledger.connectActiveDevice)
        assertEquals(keystone.enterBlockHeightManually, ledger.enterBlockHeightManually)
        assertEquals(keystone.connect, ledger.connect)
    }

    @Test
    fun theLogoAndTestTagsAreWhatActuallyDifferPerVendor() {
        val keystone = brandingOf(HWWalletEnrollment.Keystone(ur = "ur"))
        val ledger = brandingOf(HWWalletEnrollment.Ledger)

        assertEquals(HWWalletEnrollmentTag.KEYSTONE_NEW_DEVICE, keystone.newDeviceTestTag)
        assertEquals(HWWalletEnrollmentTag.KEYSTONE_CONNECT_BTN, keystone.connectTestTag)
        assertEquals(HWWalletEnrollmentTag.LEDGER_NEW_DEVICE, ledger.newDeviceTestTag)
        assertEquals(HWWalletEnrollmentTag.LEDGER_CONNECT_BTN, ledger.connectTestTag)
        assertNotEquals(keystone.logo, ledger.logo)
    }

    @Test
    fun theEnrollmentSurvivesTheRouteEncoding() {
        enrollments.forEach { enrollment ->
            val encoded = Json.encodeToString<HWWalletEnrollment>(enrollment)

            assertEquals(enrollment, HWWalletEnrollmentNavType.parseValue(encoded))
        }
    }

    @Test
    fun newDeviceImportsFromTheTipForBothVendors() =
        runTest {
            enrollments.forEach { enrollment ->
                val createAccount = readyUseCase()
                val vm = newOrActiveVM(enrollment, createAccount)
                collect { vm.state.collect { } }

                val state = assertNotNull(vm.state.value.content)
                assertEquals(brandingOf(enrollment).logo, state.logo)
                state.newDevice.onClick()
                runCurrent()

                coVerify(exactly = 1) { createAccount.invoke(enrollment, null) }
            }
        }

    @Test
    fun activeDeviceGoesToTheBirthdayQuestionForBothVendors() =
        runTest {
            enrollments.forEach { enrollment ->
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
            enrollments.forEach { enrollment ->
                val createAccount =
                    mockk<CreateHWWalletAccountUseCase>(relaxed = true) {
                        every { isReady(any()) } returns false
                    }
                val routers = List(4) { mockk<NavigationRouter>(relaxed = true) }

                newOrActiveVM(enrollment, createAccount, routers[0])
                dateVM(enrollment, createAccount, routers[1])
                estimationVM(enrollment, createAccount, routers[2])
                heightVM(enrollment, createAccount, routers[3])

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
                assertEquals(brandingOf(enrollment).enterManuallyTestTag, state.secondaryButtonTestTag)
                assertNotNull(state.secondaryButton).onClick()
                state.dialogButton?.onClick?.invoke()

                verify(exactly = 1) { navigationRouter.forward(HWHeightArgs(enrollment)) }
                verify(exactly = 1) { navigationRouter.forward(HeightInfoArgs) }
            }
        }

    @Test
    fun theEstimationScreenImportsAtTheEstimatedHeightForBothVendors() =
        runTest {
            enrollments.forEach { enrollment ->
                val createAccount = readyUseCase()
                val vm = estimationVM(enrollment, createAccount)
                collect { vm.state.collect { } }

                val state = assertNotNull(vm.state.value.content)
                assertEquals(brandingOf(enrollment).connect.resourceId(), state.primaryButton.text.resourceId())
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
                assertEquals(brandingOf(enrollment).connectTestTag, valid.primaryButtonTestTag)
                assertEquals(brandingOf(enrollment).blockHeightFieldTestTag, valid.blockHeightFieldTestTag)

                valid.primaryButton.onClick()
                runCurrent()

                coVerify(exactly = 1) {
                    createAccount.invoke(enrollment, BlockHeight.new(VALID_HEIGHT.toLong()))
                }
            }
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
    )

    private fun errorStateMapper() = ErrorMapperUseCase(sendEmail = mockk(relaxed = true))

    private fun TestScope.collect(block: suspend CoroutineScope.() -> Unit) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { block() }
    }

    private fun StringResource.resourceId(): Int = (this as StringResource.ByResource).resource
}

private const val ESTIMATED_HEIGHT = 2_500_000L

private const val VALID_HEIGHT = "2500000"
