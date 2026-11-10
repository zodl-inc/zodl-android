package co.electriccoin.zcash.ui.common.zns

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZnsRpcParserTest {
    @Test
    fun aRequestNamesTheMethodAndItsParamsOnly() {
        assertEquals(
            """{"jsonrpc":"2.0","id":1,"method":"list_names","params":{"limit":500,"offset":0}}""",
            ZnsRpcParser.requestBody("list_names", mapOf("limit" to 500, "offset" to 0))
        )
        assertEquals(
            """{"jsonrpc":"2.0","id":1,"method":"status","params":{}}""",
            ZnsRpcParser.requestBody("status", emptyMap())
        )
    }

    @Test
    fun statusReadsTheViewingKeyAndSyncState() {
        val status = ZnsRpcParser.parseStatus(rpc("""{"viewing_key":"uview1x","synced":true,"synced_height":5}"""))
        assertEquals("uview1x", status.viewingKey)
        assertTrue(status.synced)
        assertFalse(ZnsRpcParser.parseStatus(rpc("""{"viewing_key":"uview1x"}""")).synced)
    }

    @Test
    fun statusWithoutAViewingKeyIsAHostFailure() {
        assertFailsWith<ZnsHostUnavailableException> { ZnsRpcParser.parseStatus(rpc("""{"synced":true}""")) }
    }

    @Test
    fun aPageReadsRecordsAndSkipsNothingButReportsReleases() {
        val page =
            ZnsRpcParser.parseNamesPage(
                rpc(
                    """{"items":[
                        {"name":"alice","address":"u1alice","last_action":"claim"},
                        {"name":"bob","address":" u1bob ","last_action":"update"},
                        {"name":"carol","address":"","last_action":"release"}
                    ],"total":3,"limit":500,"offset":0}"""
                )
            )
        assertEquals(3, page.total)
        assertEquals(listOf("u1alice", "u1bob", null), page.names.map { it.address })
    }

    @Test
    fun aMalformedRecordRefusesTheWholeIndex() {
        val failure =
            assertFailsWith<ZnsLookupException> {
                ZnsRpcParser.parseNamesPage(
                    rpc("""{"items":[{"name":"Alice","address":"u1","last_action":"claim"}],"total":1}""")
                )
            }
        assertIs<ZnsLookupException>(failure)
        assertFalse(failure is ZnsHostUnavailableException)
        assertFailsWith<ZnsLookupException> {
            ZnsRpcParser.parseNamesPage(rpc("""{"items":[{"name":"alice","last_action":"claim"}],"total":1}"""))
        }
        assertFailsWith<ZnsLookupException> {
            ZnsRpcParser.parseNamesPage(
                rpc("""{"items":[{"name":"alice","address":"u1","last_action":"burn"}],"total":1}""")
            )
        }
    }

    @Test
    fun repliesThatAreNotAnAnswerToThisRequestAreHostFailures() {
        val bodies =
            listOf(
                "<html>bad gateway</html>",
                "[]",
                """{"error":"proxy error"}""",
                """{"jsonrpc":"2.0","id":2,"result":{"items":[],"total":0}}""",
                """{"jsonrpc":"1.0","id":1,"result":{"items":[],"total":0}}""",
                """{"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"no"}}""",
                """{"jsonrpc":"2.0","id":1}""",
                rpc("""{"items":[],"total":"3"}"""),
                rpc("""{"items":[],"total":-1}"""),
            )
        bodies.forEach { body ->
            assertFailsWith<ZnsHostUnavailableException>(body) { ZnsRpcParser.parseNamesPage(body) }
        }
    }

    @Test
    fun aNullErrorIsNotAnError() {
        val page =
            ZnsRpcParser.parseNamesPage("""{"jsonrpc":"2.0","id":1,"error":null,"result":{"items":[],"total":0}}""")
        assertEquals(0, page.total)
        assertNull(page.names.firstOrNull())
    }

    private fun rpc(result: String) = """{"jsonrpc":"2.0","id":1,"result":$result}"""
}
