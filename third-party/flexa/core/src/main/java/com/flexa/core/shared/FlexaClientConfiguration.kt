package com.flexa.core.shared

import android.content.Context
import com.flexa.core.theme.FlexaTheme
import okhttp3.OkHttpClient

/**
 * Flexa init bundle
 * @param context Activity or Application Context
 * @param publishableKey
 * @param assetAccounts user wallets
 * @param theme provide color schemes and dynamic colors
 * @param webViewThemeConfig provide color scheme for Places to Pay
 * @param okHttpClientCustomizer applied to every OkHttp client the SDK builds for the Flexa API,
 * after the SDK's own interceptors, e.g. to add an interceptor that sends the requests through a proxy
 */
class FlexaClientConfiguration(
    val context: Context,
    val publishableKey: String,
    val assetAccounts: ArrayList<AssetAccount>,
    val theme: FlexaTheme = FlexaTheme(),
    val webViewThemeConfig: String? = null,
    val okHttpClientCustomizer: ((OkHttpClient.Builder) -> Unit)? = null,
)
