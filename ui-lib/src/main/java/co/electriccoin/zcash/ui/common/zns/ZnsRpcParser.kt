package co.electriccoin.zcash.ui.common.zns

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Reads the ZNS resolver's JSON-RPC replies.
 *
 * A reply that is not a JSON-RPC 2.0 answer to this request, such as a proxy's error page, is a host failure
 * ([ZnsHostUnavailableException]), so the next host is tried. A malformed record is a final answer
 * ([ZnsLookupException]): the wallet cannot tell what else that host got wrong.
 */
internal object ZnsRpcParser {
    /** Every call is one request of its own, so a fixed id suffices. */
    const val REQUEST_ID = 1

    private val json = Json { ignoreUnknownKeys = true }

    data class Status(
        val viewingKey: String,
        val synced: Boolean,
    )

    data class NamesPage(
        val names: List<ZnsPageRecord>,
        val total: Int,
    )

    /** One record of a `list_names` page. A released name has a null [address]. */
    data class ZnsPageRecord(
        val name: ZnsName,
        val address: String?,
    )

    fun requestBody(
        method: String,
        params: Map<String, Int>
    ): String {
        val paramsJson = params.entries.joinToString(",") { (key, value) -> "\"$key\":$value" }
        return """{"jsonrpc":"2.0","id":$REQUEST_ID,"method":"$method","params":{$paramsJson}}"""
    }

    /** Reads a `status` reply. A missing `synced` reads as not synced. */
    fun parseStatus(body: String): Status {
        val result = rpcResult(body) as? JsonObject ?: throw ZnsHostUnavailableException("status is not an object")
        val viewingKey = result.string("viewing_key")
        if (viewingKey.isNullOrEmpty()) throw ZnsHostUnavailableException("status has no viewing key")
        val synced = (result["synced"] as? JsonPrimitive)?.booleanOrNull == true
        return Status(viewingKey = viewingKey, synced = synced)
    }

    /** Reads a `list_names` page. */
    fun parseNamesPage(body: String): NamesPage {
        val result = rpcResult(body) as? JsonObject ?: throw ZnsHostUnavailableException("list_names is not a page")
        val items = result["items"] as? JsonArray
        val total = (result["total"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
        if (items == null || total == null || total < 0) {
            throw ZnsHostUnavailableException("list_names is not a page")
        }
        return NamesPage(names = items.map(::parseRecord), total = total)
    }

    @Suppress("ThrowsCount")
    private fun parseRecord(element: JsonElement): ZnsPageRecord {
        val record = element as? JsonObject ?: throw ZnsLookupException("ZNS index record is not an object")
        val name =
            record.string("name")?.let(ZnsName::fromRegistryName)
                ?: throw ZnsLookupException("ZNS index record has an invalid name")
        return when (record.string("last_action")) {
            "release" -> {
                ZnsPageRecord(name = name, address = null)
            }

            "claim", "update" -> {
                val address = record.string("address")?.trim()
                if (address.isNullOrEmpty()) throw ZnsLookupException("ZNS index record has no address")
                ZnsPageRecord(name = name, address = address)
            }

            else -> {
                throw ZnsLookupException("ZNS index record has an unknown action")
            }
        }
    }

    @Suppress("ThrowsCount")
    private fun rpcResult(body: String): JsonElement {
        val reply =
            try {
                json.parseToJsonElement(body)
            } catch (_: SerializationException) {
                throw ZnsHostUnavailableException("reply is not JSON")
            } as? JsonObject ?: throw ZnsHostUnavailableException("reply is not an object")
        val id = (reply["id"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
        if (reply.string("jsonrpc") != "2.0" || id != REQUEST_ID) {
            throw ZnsHostUnavailableException("reply is not a JSON-RPC 2.0 answer to this request")
        }
        if (reply["error"].let { it != null && it !is JsonNull }) {
            throw ZnsHostUnavailableException("JSON-RPC error")
        }
        // A missing result must not read as an empty index, which would skip the fallback host.
        return reply["result"] ?: throw ZnsHostUnavailableException("reply has no result")
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}

/** A lookup that produced no answer: no resolver could be reached, or a reply could not be trusted. */
open class ZnsLookupException(
    message: String
) : Exception(message)

/** A host that failed rather than answered. The next host is tried. */
class ZnsHostUnavailableException(
    message: String
) : ZnsLookupException(message)
