package co.electriccoin.zcash.di

import co.electriccoin.zcash.ui.common.mapper.ActivityMapper
import co.electriccoin.zcash.ui.screen.pay.ExactOutputVMMapper
import co.electriccoin.zcash.ui.screen.swap.SwapVMMapper
import co.electriccoin.zcash.ui.screen.swap.quote.SwapQuoteVMMapper
import co.electriccoin.zcash.ui.screen.transactiondetail.CommonTransactionDetailMapper
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val mapperModule =
    module {
        factoryOf(::ActivityMapper)
        factoryOf(::ExactOutputVMMapper)
        factoryOf(::SwapVMMapper)
        factoryOf(::SwapQuoteVMMapper)
        factoryOf(::CommonTransactionDetailMapper)
    }
