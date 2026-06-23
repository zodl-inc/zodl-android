package co.electriccoin.zcash.ui.screen.send

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import cash.z.ecc.android.sdk.model.ZecSend
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.fixture.BalanceStateFixture
import co.electriccoin.zcash.ui.fixture.ZashiMainTopAppBarStateFixture
import co.electriccoin.zcash.ui.screen.send.ext.Saver
import co.electriccoin.zcash.ui.screen.send.model.AmountState
import co.electriccoin.zcash.ui.screen.send.model.MemoState
import co.electriccoin.zcash.ui.screen.send.model.RecipientAddressState
import co.electriccoin.zcash.ui.screen.send.model.SendAddressBookState
import co.electriccoin.zcash.ui.screen.send.model.SendStage
import co.electriccoin.zcash.ui.screen.send.view.Send
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicInteger

class SendViewTestSetup(
    private val composeTestRule: ComposeContentTestRule,
    private val initialState: SendStage,
    private val initialZecSend: ZecSend?,
    private val hasCameraFeature: Boolean
) {
    private val onBackCount = AtomicInteger(0)
    private val onSettingsCount = AtomicInteger(0)
    private val onCreateCount = AtomicInteger(0)
    private val onScannerCount = AtomicInteger(0)
    val mutableActionExecuted = MutableStateFlow(false)

    @Volatile
    private var lastSendStage: SendStage? = null

    @Volatile
    private var lastZecSend: ZecSend? = null

    fun getOnBackCount(): Int {
        composeTestRule.waitForIdle()
        return onBackCount.get()
    }

    fun getOnSettingsCount(): Int {
        composeTestRule.waitForIdle()
        return onSettingsCount.get()
    }

    fun getOnCreateCount(): Int {
        composeTestRule.waitForIdle()
        return onCreateCount.get()
    }

    fun getOnScannerCount(): Int {
        composeTestRule.waitForIdle()
        return onScannerCount.get()
    }

    fun getLastZecSend(): ZecSend? {
        composeTestRule.waitForIdle()
        return lastZecSend
    }

    @Composable
    @Suppress("TestFunctionName", "LongMethod")
    fun DefaultContent() {
        val (sendStage, setSendStage) =
            rememberSaveable(stateSaver = SendStage.Saver) { mutableStateOf(initialState) }

        lastSendStage = sendStage

        val onBackAction = {
            onBackCount.incrementAndGet()
            when (sendStage) {
                SendStage.Form -> {}

                SendStage.Proposing -> {}

                is SendStage.SendFailure -> {
                    setSendStage(SendStage.Form)
                }
            }
        }

        BackHandler {
            onBackAction()
        }

        val (zecSend, setZecSend) =
            rememberSaveable(stateSaver = ZecSend.Saver) { mutableStateOf(initialZecSend) }

        lastZecSend = zecSend

        val locale = LocalConfiguration.current.locales[0]

        ZcashTheme {
            // TODO [#1260]: Cover Send.Form screen UI with tests
            // TODO [#1260]: https://github.com/Electric-Coin-Company/zashi-android/issues/1260
            Send(
                balanceWidgetState = BalanceStateFixture.new(),
                sendStage = sendStage,
                onCreateZecSend = setZecSend,
                onBack = onBackAction,
                onQrScannerOpen = {
                    onScannerCount.incrementAndGet()
                },
                hasCameraFeature = hasCameraFeature,
                recipientAddressState = RecipientAddressState("", AddressType.Invalid()),
                onRecipientAddressChange = {
                    // TODO [#1260]: Cover Send.Form screen UI with tests
                    // TODO [#1260]: https://github.com/Electric-Coin-Company/zashi-android/issues/1260
                },
                setAmountState = {},
                amountState =
                    AmountState.newFromZec(
                        value = "",
                        fiatValue = "",
                        isTransparentOrTextRecipient = false,
                        exchangeRateState = ExchangeRateState.OptedOut,
                        locale = locale
                    ),
                setMemoState = {},
                memoState = MemoState.new(""),
                selectedAccount = null,
                exchangeRateState = ExchangeRateState.OptedOut,
                sendAddressBookState =
                    SendAddressBookState(
                        mode = SendAddressBookState.Mode.PICK_FROM_ADDRESS_BOOK,
                        isHintVisible = false,
                        onButtonClick = {}
                    ),
                zashiMainTopAppBarState =
                    ZashiMainTopAppBarStateFixture.new(
                        settingsButton =
                            IconButtonState(
                                icon = R.drawable.ic_app_bar_settings,
                                contentDescription =
                                    stringRes(co.electriccoin.zcash.ui.R.string.scan_openSettings),
                            ) {
                                onSettingsCount.incrementAndGet()
                            }
                    )
            )
        }
    }

    fun setDefaultContent() {
        composeTestRule.setContent {
            DefaultContent()
        }
    }
}
