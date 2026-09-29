package co.electriccoin.zcash.ledger.di

import org.koin.dsl.module

/**
 * Everything the Ledger feature contributes to the app's Koin graph — its providers, data sources,
 * repositories, use cases, view models, and the implementations of ui-lib's Ledger contracts (see
 * LedgerContracts.kt). Wired in ZcashApplication.startKoin next to the migration and voting modules.
 */
val featureLedgerModule =
    module {
    }
