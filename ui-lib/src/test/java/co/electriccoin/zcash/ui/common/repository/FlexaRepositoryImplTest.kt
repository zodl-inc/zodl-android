package co.electriccoin.zcash.ui.common.repository

import android.app.Application
import co.electriccoin.zcash.ui.common.model.ServerConnectionMode
import co.electriccoin.zcash.ui.common.provider.FLEXA_OHTTP_PUBLISHABLE_KEY
import co.electriccoin.zcash.ui.common.provider.FlexaOhttpInterceptor
import co.electriccoin.zcash.ui.common.provider.IsTorEnabledStorageProvider
import co.electriccoin.zcash.ui.common.usecase.ObserveServerConnectionModeUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The Flexa SDK gets the gateway's placeholder key, and its OkHttp clients end in the OHTTP interceptor. */
@OptIn(ExperimentalCoroutinesApi::class)
class FlexaRepositoryImplTest {
    private val tor = MutableStateFlow<Boolean?>(null)
    private val mode = MutableStateFlow(ServerConnectionMode.PUBLIC)

    private val repository =
        FlexaRepositoryImpl(
            application = mockk<Application>(relaxed = true),
            configurationRepository = mockk(),
            accountDataSource = mockk(),
            httpClientProvider = mockk(),
            isTorEnabledStorageProvider = mockk<IsTorEnabledStorageProvider> { every { observe() } returns tor },
            observeServerConnectionMode =
                mockk<ObserveServerConnectionModeUseCase> { every { this@mockk.invoke() } returns mode },
        )

    @Test
    fun imagesAndWebViewsGoDirectOnlyOnAPublicConnectionWithTorOff() =
        runTest(UnconfinedTestDispatcher()) {
            val allowed = assertNotNull(repository.getFlexaClientConfiguration().directConnectionsAllowed)
            // Nothing is known yet: not allowed.
            assertFalse(allowed())

            val job = launch { repository.trackDirectConnections() }
            assertTrue(allowed())

            tor.value = true
            assertFalse(allowed())

            tor.value = false
            mode.value = ServerConnectionMode.PRIVATE
            assertFalse(allowed())

            mode.value = ServerConnectionMode.MANUAL
            assertTrue(allowed())
            job.cancel()
        }

    @Test
    fun theSdkGetsThePlaceholderKey() {
        val configuration = repository.getFlexaClientConfiguration()

        assertEquals("publishable_live_zodl_gateway_placeholder", configuration.publishableKey)
        assertEquals(FLEXA_OHTTP_PUBLISHABLE_KEY, configuration.publishableKey)
    }

    @Test
    fun theOhttpInterceptorComesAfterTheSdkInterceptors() {
        val sdkInterceptor = okhttp3.Interceptor { it.proceed(it.request()) }
        val builder = OkHttpClient.Builder().addInterceptor(sdkInterceptor)

        assertNotNull(repository.getFlexaClientConfiguration().okHttpClientCustomizer).invoke(builder)

        val interceptors = builder.build().interceptors
        assertEquals(2, interceptors.size)
        assertEquals(sdkInterceptor, interceptors[0])
        assertIs<FlexaOhttpInterceptor>(interceptors[1])
    }
}
