package com.flexa.core.data.rest

import com.flexa.core.Flexa
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BuildFlexaClientTest {

    private val sdkInterceptor = Interceptor { it.proceed(it.request()) }
    private val appInterceptor = Interceptor { it.proceed(it.request()) }

    @After
    fun tearDown() {
        Flexa.okHttpClientCustomizer = null
    }

    @Test
    fun `without a customizer the client is built unchanged`() {
        val client = OkHttpClient.Builder().addInterceptor(sdkInterceptor).buildFlexaClient()

        assertEquals(listOf(sdkInterceptor), client.interceptors)
    }

    @Test
    fun `the customizer runs after the SDK's own interceptors`() {
        Flexa.okHttpClientCustomizer = { it.addInterceptor(appInterceptor) }

        val client = OkHttpClient.Builder().addInterceptor(sdkInterceptor).buildFlexaClient()

        assertEquals(2, client.interceptors.size)
        assertSame(sdkInterceptor, client.interceptors[0])
        assertSame(appInterceptor, client.interceptors[1])
    }
}
