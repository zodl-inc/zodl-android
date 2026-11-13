package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.exception.TransactionEncoderException
import cash.z.ecc.android.sdk.exception.proposalTooLargeReason

/**
 * Whether this failure, or anything it wraps, is the SDK's proposal size gate
 * ([TransactionEncoderException.ProposalTooLargeException]): the wallet has too many notes to spend in one
 * transaction.
 *
 * The typed reason is looked up by the SDK's [proposalTooLargeReason], which follows the cause chain, the
 * create path's `rootCause` and suppressed exceptions.
 */
fun Throwable.isProposalTooLarge(): Boolean = proposalTooLargeReason() != null
