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
 * @param directConnectionsAllowed asked before the SDK connects to Flexa directly, outside the API
 * clients [okHttpClientCustomizer] reaches: image downloads and web views (merchant directory, how to
 * pay, legal pages). When it returns false, images are not downloaded (the placeholder shows) and a
 * web view first shows a disclosure and loads only after the user agrees. Null = always allowed.
 */
class FlexaClientConfiguration(
    val context: Context,
    val publishableKey: String,
    val assetAccounts: ArrayList<AssetAccount>,
    val theme: FlexaTheme = FlexaTheme(),
    val webViewThemeConfig: String? = null,
    val okHttpClientCustomizer: ((OkHttpClient.Builder) -> Unit)? = null,
    val directConnectionsAllowed: (() -> Boolean)? = null,
)
