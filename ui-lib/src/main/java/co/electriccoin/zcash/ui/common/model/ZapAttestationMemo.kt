package co.electriccoin.zcash.ui.common.model

/**
 * A ZAP1 or legacy NSM1 marker in a memo already decrypted by the wallet.
 *
 * The wire type is a two-digit lowercase hexadecimal byte, followed by a 32-byte payload hash
 * encoded as 64 lowercase hexadecimal characters. Type 09 carries a Merkle root rather than a
 * leaf hash. See Frontier-Compute/zap1 ONCHAIN_PROTOCOL.md, version 3.0.0, and issue #2172.
 *
 * Recognition only classifies the memo text. It does not verify the event claim, a Merkle proof,
 * or a root's binding to a Zcash transaction. No external lookup is needed to render the card.
 */
data class ZapAttestationMemo(
    val protocol: Protocol,
    val eventType: String,
    val payloadHash: String,
) {
    /** Unknown wire bytes remain visibly unassigned, without inventing a registry entry. */
    val eventLabel: String
        get() = EVENT_LABELS[eventType] ?: "Unknown event (0x$eventType)"

    enum class Protocol {
        ZAP1,
        NSM1
    }

    companion object {
        private val MEMO_PATTERN = Regex("^(ZAP1|NSM1):([0-9a-f]{2}):([0-9a-f]{64})$")

        private val EVENT_LABELS =
            mapOf(
                "01" to "Program entry",
                "02" to "Ownership attestation",
                "03" to "Contract anchor",
                "04" to "Deployment",
                "05" to "Hosting payment",
                "06" to "Shield renewal",
                "07" to "Transfer",
                "08" to "Exit",
                "09" to "Merkle root",
                "0a" to "Staking deposit",
                "0b" to "Staking withdrawal",
                "0c" to "Staking reward",
                "0d" to "Governance proposal",
                "0e" to "Governance vote",
                "0f" to "Governance result",
                "40" to "Agent register",
                "41" to "Agent policy",
                "42" to "Agent action",
            )

        /** Returns null for ordinary or malformed memos so their existing rendering is preserved. */
        fun parse(memo: String): ZapAttestationMemo? {
            val match = MEMO_PATTERN.matchEntire(memo.trim()) ?: return null
            val (prefix, type, hash) = match.destructured
            return ZapAttestationMemo(
                protocol = Protocol.valueOf(prefix),
                eventType = type,
                payloadHash = hash
            )
        }
    }
}
