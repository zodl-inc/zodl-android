package co.electriccoin.zcash.di

import co.electriccoin.zcash.ui.common.mapper.ActivityMapper
import co.electriccoin.zcash.ui.common.model.voting.VotingErrorMapper
import co.electriccoin.zcash.ui.screen.pay.ExactOutputVMMapper
import co.electriccoin.zcash.ui.screen.swap.ExactInputVMMapper
import co.electriccoin.zcash.ui.screen.swap.quote.SwapQuoteVMMapper
import co.electriccoin.zcash.ui.screen.transactiondetail.CommonTransactionDetailMapper
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val mapperModule =
    module {
        factoryOf(::ActivityMapper)
        factoryOf(::ExactOutputVMMapper)
        factoryOf(::ExactInputVMMapper)
        factoryOf(::SwapQuoteVMMapper)
        factoryOf(::CommonTransactionDetailMapper)
        factoryOf(::VotingErrorMapper)
    }
