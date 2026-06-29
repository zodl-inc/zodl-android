package co.electriccoin.zcash.di

import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.AccountDataSourceImpl
import co.electriccoin.zcash.ui.common.datasource.ExchangeRateDataSource
import co.electriccoin.zcash.ui.common.datasource.ExchangeRateDataSourceImpl
import co.electriccoin.zcash.ui.common.datasource.MayaSwapDataSource
import co.electriccoin.zcash.ui.common.datasource.MessageAvailabilityDataSource
import co.electriccoin.zcash.ui.common.datasource.MessageAvailabilityDataSourceImpl
import co.electriccoin.zcash.ui.common.datasource.NearSwapDataSource
import co.electriccoin.zcash.ui.common.datasource.ProposalDataSource
import co.electriccoin.zcash.ui.common.datasource.ProposalDataSourceImpl
import co.electriccoin.zcash.ui.common.datasource.RestoreTimestampDataSource
import co.electriccoin.zcash.ui.common.datasource.RestoreTimestampDataSourceImpl
import co.electriccoin.zcash.ui.common.datasource.SwapDataSource
import co.electriccoin.zcash.ui.common.datasource.WalletSnapshotDataSource
import co.electriccoin.zcash.ui.common.datasource.WalletSnapshotDataSourceImpl
import co.electriccoin.zcash.ui.common.datasource.ZashiSpendingKeyDataSource
import co.electriccoin.zcash.ui.common.datasource.ZashiSpendingKeyDataSourceImpl
import co.electriccoin.zcash.ui.common.model.SwapProvider
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val dataSourceModule =
    module {
        singleOf(::AccountDataSourceImpl) bind AccountDataSource::class
        singleOf(::ZashiSpendingKeyDataSourceImpl) bind ZashiSpendingKeyDataSource::class
        singleOf(::ProposalDataSourceImpl) bind ProposalDataSource::class
        singleOf(::RestoreTimestampDataSourceImpl) bind RestoreTimestampDataSource::class
        singleOf(::MessageAvailabilityDataSourceImpl) bind MessageAvailabilityDataSource::class
        singleOf(::WalletSnapshotDataSourceImpl) bind WalletSnapshotDataSource::class
        single<SwapDataSource>(named(SwapProvider.NEAR)) {
            NearSwapDataSource(
                nearApiProvider = get(),
                tokenIconProvider = get(),
                tokenNameProvider = get(),
                blockchainProvider = get(),
                synchronizerProvider = get(),
            )
        }
        single<SwapDataSource>(named(SwapProvider.MAYA)) {
            MayaSwapDataSource(
                swapkitApiProvider = get(),
                tokenIconProvider = get(),
                tokenNameProvider = get(),
                blockchainProvider = get(),
                synchronizerProvider = get(),
            )
        }
        singleOf(::ExchangeRateDataSourceImpl) bind ExchangeRateDataSource::class
    }
