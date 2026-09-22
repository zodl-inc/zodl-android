package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.Memo
import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZecSend
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.LedgerOperationUnsupportedException
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.repository.EphemeralAddressRepository
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository

class CreateIncreaseEphemeralGapLimitProposalUseCase(
    private val keystoneProposalRepository: KeystoneProposalRepository,
    private val zashiProposalRepository: ZashiProposalRepository,
    private val accountDataSource: AccountDataSource,
    private val ephemeralAddressRepository: EphemeralAddressRepository
) {
    @Suppress("TooGenericExceptionCaught", "MagicNumber", "UseCheckOrError", "ThrowsCount")
    suspend operator fun invoke() {
        val address =
            ephemeralAddressRepository.get()?.address
                ?: throw IllegalStateException("Ephemeral address is null")
        val normalized =
            ZecSend(
                destination = WalletAddress.Transparent.new(address),
                amount = Zatoshi(100),
                memo = Memo(""),
                proposal = null
            )
        try {
            when (accountDataSource.getSelectedAccount()) {
                is KeystoneAccount -> {
                    keystoneProposalRepository.createProposal(normalized)
                    keystoneProposalRepository.createPCZTFromProposal()
                }

                is LedgerAccount -> {
                    throw LedgerOperationUnsupportedException()
                }

                is ZashiAccount -> {
                    zashiProposalRepository.createProposal(normalized)
                }
            }
        } catch (e: Exception) {
            keystoneProposalRepository.clear()
            zashiProposalRepository.clear()
            throw e
        }
    }
}
