package co.electriccoin.zcash.ui.common.util

/**
 * Parses ZAP1 and legacy NSM1 attestation memos into structured data.
 */
object Zap1MemoFormatter {
    private val pattern = Regex("^(ZAP1|NSM1):([0-9a-fA-F]{2}):([0-9a-fA-F]{64})$")

    private val events = mapOf(
        "01" to "PROGRAM_ENTRY",
        "02" to "OWNERSHIP_ATTEST",
        "03" to "CONTRACT_ANCHOR",
        "04" to "DEPLOYMENT",
        "05" to "HOSTING_PAYMENT",
        "06" to "SHIELD_RENEWAL",
        "07" to "TRANSFER",
        "08" to "EXIT",
        "09" to "MERKLE_ROOT",
        "0a" to "STAKING_DEPOSIT",
        "0b" to "STAKING_WITHDRAW",
        "0c" to "STAKING_REWARD",
        "0d" to "GOVERNANCE_PROPOSAL",
        "0e" to "GOVERNANCE_VOTE",
        "0f" to "GOVERNANCE_RESULT"
    )

    fun parse(memo: String): Attestation? {
        val match = pattern.matchEntire(memo.trim()) ?: return null
        val prefix = match.groupValues[1]
        val typeHex = match.groupValues[2].lowercase()
        val hash = match.groupValues[3]

        return Attestation(
            prefix = prefix,
            typeHex = typeHex,
            event = events[typeHex] ?: "TYPE_0x$typeHex",
            hash = hash
        )
    }

    fun format(memo: String): String? {
        val attestation = parse(memo) ?: return null
        return "ZAP1: ${attestation.event}  ${attestation.shortHash}"
    }

    data class Attestation(
        val prefix: String,
        val typeHex: String,
        val event: String,
        val hash: String
    ) {
        val shortHash get() = "${hash.take(12)}..."
        val isLegacy get() = prefix == "NSM1"
    }
}
