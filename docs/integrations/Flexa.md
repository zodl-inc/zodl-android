# Flexa Integration
- Flexa is a digital payment platform that allows merchants to accept various digital assets, including 
  cryptocurrencies, as payment. It provides a seamless and secure way for users to pay with their digital wallets at 
  physical stores.
- The Android Flexa libraries come from the [GitHub repository](https://github.com/flexa/flexa-android) (MIT).
- The `core` and `spend` modules are vendored as source in `third-party/flexa/` (Gradle projects `:flexa-core` and
  `:flexa-spend`), with one patch that lets the app add an OkHttp interceptor; see `third-party/flexa/README.md`.
  The F-Droid build compiles them from source like the rest of the app.

# Network routing
- The SDK's API calls (`api.flexa.co`: login, account, assets, brands, rates, commerce sessions and the
  `/events` stream) go through the OHTTP relay and the mainnet gateway as inner requests to `flexa.zodl.com`
  (`FlexaOhttpInterceptor`, installed with `FlexaClientConfiguration.okHttpClientCustomizer`). With Tor on, the
  relay leg goes over Tor. Flexa sees the gateway's address, not the user's.
- The app gives the SDK the placeholder key `publishable_live_zodl_gateway_placeholder`; the gateway replaces
  it with the real publishable key. No build carries the key.
- The event stream is answered as it arrives; the gateway ends it after at most 80 s and the SDK reconnects with
  its Last-Event-ID.
- Not proxied: the SDK's web views (merchant directory, how to pay, privacy and terms pages) and the brand and
  asset images, which Android's WebView and Coil load directly from Flexa, also with Tor on.
- Flexa is mainnet only; the testnet build does not offer it.

# Setup
To bump the Flexa libraries follow `third-party/flexa/README.md`.
