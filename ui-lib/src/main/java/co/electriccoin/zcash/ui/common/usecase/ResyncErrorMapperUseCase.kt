package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceContent
import co.electriccoin.zcash.ui.design.util.stringRes

class ResyncErrorMapperUseCase(
    private val errorStateMapper: ErrorMapperUseCase,
) {
    fun mapToState(error: LceContent.Error) =
        errorStateMapper.mapToState(
            error = error,
            title = stringRes(R.string.resyncWallet_failedTitle),
            message = stringRes(R.string.resyncWallet_failedDescription),
        )
}
