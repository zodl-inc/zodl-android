package co.electriccoin.zcash.ui.screen.send

import android.os.Bundle
import android.os.Parcel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.ui.common.usecase.ObserveABContactPickedUseCase
import co.electriccoin.zcash.ui.screen.send.model.RecipientAddressState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SendViewModelRestorationTest {
    private val store = ViewModelStore()
    private var nextId = 0
    private val route =
        Send("shared address", cash.z.ecc.sdk.model.AddressType.UNIFIED, amount = 10_000L, memo = "hello")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun routeRecipientIsPresentBeforeCollectionAndSurvivesProcessDeath() {
        val handle = SavedStateHandle()
        val original = create(handle)
        val restored = create(roundTrip(handle))

        assertEquals(
            RecipientAddressState.new("shared address", AddressType.Unified),
            original.recipientAddressState.value
        )
        assertEquals(original.recipientAddressState.value, restored.recipientAddressState.value)
    }

    @Test
    fun editedRecipientAndValidationSurviveProcessDeathInsteadOfReapplyingRoute() {
        listOf(
            AddressType.Transparent,
            AddressType.Shielded,
            AddressType.Tex,
            AddressType.Unified,
            AddressType.Invalid("invalid edited address"),
            null
        ).forEach { type ->
            val handle = SavedStateHandle()
            val edited = RecipientAddressState.new("edited address", type)
            create(handle).onRecipientAddressChanged(edited)

            val restored = create(roundTrip(handle)).recipientAddressState.value
            assertEquals(edited.address, restored.address)
            if (type is AddressType.Invalid) {
                assertEquals(type.reason, assertIs<AddressType.Invalid>(restored.type).reason)
            } else {
                assertEquals(type, restored.type)
            }
        }
    }

    @Test
    fun clearedRecipientStaysEmptyAfterProcessDeath() {
        val handle = SavedStateHandle()
        val cleared = RecipientAddressState.new("", null)
        create(handle).onRecipientAddressChanged(cleared)

        assertEquals(cleared, create(roundTrip(handle)).recipientAddressState.value)
    }

    @Test
    fun routeDefaultsDistinguishBareAddressFromMemoOnlyPayment() {
        assertNull(Send().initialAmount())
        assertEquals(0L, Send(memo = "hello").initialAmount()?.value)
        assertEquals(10_000L, route.initialAmount()?.value)
    }

    private fun create(handle: SavedStateHandle): SendViewModel {
        val contacts = mockk<ObserveABContactPickedUseCase>()
        every { contacts() } returns emptyFlow()
        return SendViewModel(
            args = route,
            savedStateHandle = handle,
            exchangeRateRepository = mockk(relaxed = true),
            observeContactByAddress = mockk(),
            observeContactPicked = contacts,
            createProposal = mockk(),
            observeWalletAccounts = mockk(),
            navigateToSelectRecipient = mockk(),
            navigationRouter = mockk()
        ).also { store.put("send-${nextId++}", it) }
    }

    private fun roundTrip(handle: SavedStateHandle): SavedStateHandle {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(handle.savedStateProvider().saveState())
            parcel.setDataPosition(0)
            val restored: Bundle = requireNotNull(parcel.readBundle(javaClass.classLoader))
            SavedStateHandle.createHandle(restored, null)
        } finally {
            parcel.recycle()
        }
    }
}
