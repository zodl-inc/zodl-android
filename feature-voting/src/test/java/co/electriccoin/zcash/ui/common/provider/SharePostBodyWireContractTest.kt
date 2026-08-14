package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.ui.common.model.voting.EncryptedShare
import co.electriccoin.zcash.ui.common.model.voting.SharePayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Pins [SharePayload.toApiBody]'s output against `zcash_voting::wire::VoteShareWire`
 * (MOB-1678, zcash_voting 3.0): the crate's wire struct dropped `all_enc_shares` in
 * 2.0-rc.4, so the POST body must never carry it, and every remaining field must match
 * the wire struct's names and base64 byte encoding exactly.
 *
 * Parses with kotlinx.serialization rather than `org.json` — the latter is an
 * Android-framework stub that throws under this module's plain-JVM `unitTests
 * .isReturnDefaultValues = true` setup, unlike the production code's `org.json` usage,
 * which never round-trips its own output back through parsing.
 */
class SharePostBodyWireContractTest {
    @Test
    fun toApiBodyOmitsAllEncSharesAndMatchesTheWireFieldSet() {
        val body = Json.parseToJsonElement(makeSharePayload().toApiBody(ROUND_ID_HEX)).jsonObject

        assertFalse(body.containsKey("all_enc_shares"))
        assertEquals(
            setOf(
                "shares_hash",
                "proposal_id",
                "vote_decision",
                "enc_share",
                "share_index",
                "tree_position",
                "vote_round_id",
                "share_comms",
                "primary_blind",
                "submit_at"
            ),
            body.keys
        )
    }

    @Test
    fun toApiBodyCarriesTheRoundIdAsLowercaseHexVerbatim() {
        val body = Json.parseToJsonElement(makeSharePayload().toApiBody(ROUND_ID_HEX)).jsonObject

        assertEquals(ROUND_ID_HEX, body.getValue("vote_round_id").jsonPrimitive.content)
    }

    private fun makeSharePayload(): SharePayload =
        SharePayload(
            sharesHash = ByteArray(32) { 1 },
            proposalId = 3,
            voteDecision = 1,
            encShare = EncryptedShare(c1 = ByteArray(32) { 2 }, c2 = ByteArray(32) { 3 }, shareIndex = 0),
            treePosition = 42L,
            allEncShares =
                listOf(
                    EncryptedShare(c1 = ByteArray(32) { 4 }, c2 = ByteArray(32) { 5 }, shareIndex = 0),
                    EncryptedShare(c1 = ByteArray(32) { 6 }, c2 = ByteArray(32) { 7 }, shareIndex = 1)
                ),
            shareComms = listOf(ByteArray(32) { 8 }),
            primaryBlind = ByteArray(32) { 9 },
            submitAt = 99L
        )

    private companion object {
        val ROUND_ID_HEX = "01".repeat(32)
    }
}
