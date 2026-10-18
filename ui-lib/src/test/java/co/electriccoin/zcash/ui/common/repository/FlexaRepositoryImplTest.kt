package co.electriccoin.zcash.ui.common.repository

import android.app.Application
import co.electriccoin.zcash.ui.common.provider.FLEXA_OHTTP_PUBLISHABLE_KEY
import co.electriccoin.zcash.ui.common.provider.FlexaOhttpInterceptor
import io.mockk.mockk
import okhttp3.OkHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** The Flexa SDK gets the gateway's placeholder key, and its OkHttp clients end in the OHTTP interceptor. */
class FlexaRepositoryImplTest {
    private val repository =
        FlexaRepositoryImpl(
            application = mockk<Application>(relaxed = true),
            configurationRepository = mockk(),
            accountDataSource = mockk(),
            httpClientProvider = mockk(),
        )

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
