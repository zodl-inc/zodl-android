package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.model.TransactionSizeBound
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.provider.SignerCapabilitiesProvider

/**
 * The size bound to create the transactions of [proposal] within when [account] signs them.
 *
 * Shielding and the Orchard to Ironwood migration sweep are never bounded: the migration does not take its limits from
 * the device, and shielding is not a send of the user's choosing.
 */
suspend fun SignerCapabilitiesProvider.sizeBoundFor(
    account: WalletAccount,
    proposal: TransactionProposal
): TransactionSizeBound =
    when (proposal) {
        is ShieldTransactionProposal,
        is MigrationSweepTransactionProposal -> TransactionSizeBound.CONSENSUS

        is RegularTransactionProposal,
        is Zip321TransactionProposal,
        is ExactInputSwapTransactionProposal,
        is ExactOutputSwapTransactionProposal -> forAccount(account).sizeBound
    }
