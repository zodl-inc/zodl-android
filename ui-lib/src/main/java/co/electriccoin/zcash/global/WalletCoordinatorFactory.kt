package co.electriccoin.zcash.global

import android.content.Context
import cash.z.ecc.android.sdk.WalletCoordinator
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.provider.IsExchangeRateEnabledStorageProvider
import co.electriccoin.zcash.ui.common.provider.IsTorEnabledStorageProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider

internal operator fun WalletCoordinator.Companion.invoke(
    context: Context,
    persistableWalletProvider: PersistableWalletProvider,
    isTorEnabledStorageProvider: IsTorEnabledStorageProvider,
    isExchangeRateEnabledStorageProvider: IsExchangeRateEnabledStorageProvider
): WalletCoordinator =
    WalletCoordinator(
        context = context,
        persistableWallet = persistableWalletProvider.persistableWallet,
        accountName = context.getString(R.string.accounts_zashi),
        keySource = "zashi",
        isTorEnabled = isTorEnabledStorageProvider.observe(),
        isExchangeRateEnabled = isExchangeRateEnabledStorageProvider.observe()
    )
