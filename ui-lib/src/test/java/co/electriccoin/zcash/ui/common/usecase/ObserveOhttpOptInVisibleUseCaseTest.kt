package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.ServerConnectionMode
import co.electriccoin.zcash.ui.common.provider.IsOhttpFallbackActiveProvider
import co.electriccoin.zcash.ui.common.provider.IsOhttpOptInDismissedProvider
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveOhttpOptInVisibleUseCaseTest {
    private val mode = MutableStateFlow(ServerConnectionMode.MANUAL)
    private val dismissed = MutableStateFlow(false)
    private val outageFallback = MutableStateFlow(false)
    private var hasGateway = true

    private val useCase =
        ObserveOhttpOptInVisibleUseCase(
            observeServerConnectionMode = mockk { every { this@mockk.invoke() } returns mode },
            isOhttpOptInDismissedProvider =
                mockk<IsOhttpOptInDismissedProvider> { every { observe() } returns dismissed },
            isOhttpFallbackActiveProvider =
                mockk<IsOhttpFallbackActiveProvider> { every { observe() } returns outageFallback },
            lightWalletEndpointProvider =
                mockk<LightWalletEndpointProvider> { every { hasOhttpGateway() } answers { hasGateway } },
        )

    @Test
    fun visibleForManualAndPublicUntilOptedOut() =
        runTest {
            assertEquals(true, useCase().first())
            mode.value = ServerConnectionMode.PUBLIC
            assertEquals(true, useCase().first())
            dismissed.value = true
            assertEquals(false, useCase().first())
        }

    /**
     * The opt-in prompt is hidden when Public is selected due to an outage. It reappears
     * when the user picks Public or Manual deliberately.
     */
    @Test
    fun hiddenWhilePublicIsOnlyTheOutageFallback() =
        runTest {
            mode.value = ServerConnectionMode.PUBLIC
            outageFallback.value = true
            assertEquals(false, useCase().first())

            outageFallback.value = false
            assertEquals(true, useCase().first())
        }

    @Test
    fun hiddenWhenTheBuildHasOhttpOff() =
        runTest {
            hasGateway = false
            mode.value = ServerConnectionMode.PUBLIC
            assertEquals(false, useCase().first())
        }

    @Test
    fun hiddenWhilePrivate() =
        runTest {
            mode.value = ServerConnectionMode.PRIVATE
            assertEquals(false, useCase().first())
        }
}
