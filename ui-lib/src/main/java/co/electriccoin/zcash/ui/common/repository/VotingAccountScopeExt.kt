package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.ext.toHex
import cash.z.ecc.android.sdk.model.AccountUuid

fun AccountUuid.toVotingAccountScopeId(): String = value.toHex()
