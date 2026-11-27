package com.flexa.core.data.rest

import android.os.Build
import android.util.Log
import com.flexa.BuildConfig
import com.flexa.core.data.storage.SecuredPreferences
import com.flexa.core.domain.rest.IRestRepository
import com.flexa.core.entity.Account
import com.flexa.core.entity.CommerceSession
import com.flexa.core.entity.ExchangeRate
import com.flexa.core.entity.ExchangeRatesResponse
import com.flexa.core.entity.OneTimeKey
import com.flexa.core.entity.OneTimeKeyResponse
import com.flexa.core.entity.SseEvent
import com.flexa.core.entity.TokenPatch
import com.flexa.core.entity.TokensResponse
import com.flexa.core.entity.TransactionFee
import com.flexa.core.entity.error.ApiException
import com.flexa.core.shared.Asset
import com.flexa.core.shared.AssetsResponse
import com.flexa.core.shared.Brand
import com.flexa.core.shared.BrandsResponse
import com.flexa.core.shared.FlexaConstants
import com.flexa.core.toApiException
import com.flexa.identity.create_id.AccountsRequest
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.net.HttpURLConnection
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class RestRepository(
    private val preferences: SecuredPreferences,
) : IRestRepository {

    companion object {
        const val SCHEME = "https"
        val host = if (!BuildConfig.DEBUG_API_HOST.isNullOrEmpty()) {
            BuildConfig.DEBUG_API_HOST
        } else {
            "api.flexa.co"
        }
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val EMPTY_REQUEST = ByteArray(0).toRequestBody()
        val json = Json { ignoreUnknownKeys = true }
    }

    private val okHttpProvider = OkHttpProvider(preferences)

    override suspend fun tokens(email: String, challenge: String): TokensResponse =
        suspendCancellableCoroutine { cont ->
            val url = HttpUrl.Builder().scheme(SCHEME).host(host)
                .addPathSegment("tokens")
                .build()

            val body = buildJsonObject {
                val deviceId =
                    preferences.getStringSynchronously(FlexaConstants.UNIQUE_IDENTIFIER) ?: ""
                put("challenge", challenge)
                put("device_id", deviceId)
                put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("email", email)
            }.run { toString().toRequestBody(mediaType) }

            val request: Request = Request.Builder()
                .url(url).post(body).build()

            runCatching { okHttpProvider.loginClient.newCall(request).execute() }
                .onSuccess { response ->
                    try {
                        val raw = response.body?.string().toString()
                        val jsonResponse = json.parseToJsonElement(raw)
                        when {
                            "id" in jsonResponse.jsonObject
                                    && "status" in jsonResponse.jsonObject -> {
                                val id = jsonResponse.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                                val status =
                                    jsonResponse.jsonObject["status"]?.jsonPrimitive?.contentOrNull
                                if (id != null && status != null) {
                                    cont.resume(TokensResponse.Success(id, status))
                                } else {
                                    cont.resumeWithException(Exception(raw))
                                }
                            }

                            "error" in jsonResponse.jsonObject -> {
                                val error = jsonResponse.jsonObject["error"]
                                val code =
                                    error?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull
                                val message =
                                    error?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                                if (code != null && message != null) {
                                    cont.resume(TokensResponse.Error(code, message))
                                } else {
                                    cont.resumeWithException(Exception(raw))
                                }
                            }

                            else -> {
                                cont.resumeWithException(Exception(raw))
                            }
                        }

                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }
                .onFailure { ex -> cont.resumeWithException(ex) }
        }

    override suspend fun accounts(request: AccountsRequest): Int =
        suspendCancellableCoroutine { cont ->
            val url = HttpUrl.Builder().scheme(SCHEME).host(host)
                .addPathSegment("accounts")
                .build()

            val body = json.encodeToString(request).toRequestBody(mediaType)

            val req: Request = Request.Builder()
                .url(url).post(body).build()

            runCatching { okHttpProvider.loginClient.newCall(req).execute() }
                .onSuccess { response ->
                    when {
                        response.isSuccessful -> {
                            cont.resume(response.code)
                        }

                        else -> {
                            val apiException = response.toApiException()
                            cont.resumeWithException(apiException)
                        }
                    }
                }
                .onFailure { ex -> cont.resumeWithException(ex) }
        }


    override suspend fun patchTokens(
        id: String,
        verifier: String,
        challenge: String,
        code: String?,
        link: String?,
    ): TokenPatch = suspendCancellableCoroutine {
        okHttpProvider.tokenProvider.dropCache()

        val url = HttpUrl.Builder()
            .scheme(SCHEME).host(host)
            .addPathSegment("tokens")
            .addPathSegment(id)
            .build()

        val body = buildJsonObject {
            put("challenge", challenge)
            put("verifier", verifier)
            code?.let { data -> put("code", data) }
            link?.let { data -> put("link", data) }
        }.run { toString().toRequestBody(mediaType) }

        val request: Request = Request.Builder()
            .url(url).patch(body).build()

        runCatching { okHttpProvider.loginClient.newCall(request).execute() }
            .fold(
                onSuccess = { response ->
                    runCatching {
                        val raw = response.body?.string().toString()
                        json.decodeFromString<TokenPatch>(raw)
                    }.fold(
                        onSuccess = { dto -> it.resume(dto) },
                        onFailure = { ex -> it.resumeWithException(ex) }
                    )
                },
                onFailure = { ex -> it.resumeWithException(ex) }
            )
    }

    override suspend fun getOneTimeKeys(assetIds: List<String>): OneTimeKeyResponse =
        suspendCancellableCoroutine {
            val builder = HttpUrl.Builder().scheme(SCHEME).host(host)
                .addPathSegment("accounts")
                .addPathSegment("me")
                .addPathSegment("one_time_keys")

            val url = builder.build()

            val body = buildJsonObject {
                put("data", buildJsonArray {
                    assetIds.forEach { assetId -> add(buildJsonObject { put("asset", assetId) }) }
                })
            }.run { toString().toRequestBody(mediaType) }

            val request: Request = Request.Builder().url(url).put(body).build()

            runCatching { okHttpProvider.client.newCall(request).execute() }
                .onSuccess { response ->
                    runCatching {
                        val raw = response.body?.string().toString()
                        val jsonElement = json.parseToJsonElement(raw)
                        val hasMore = jsonElement.jsonObject["has_more"].toString().toBoolean()
                        val dataObject =
                            jsonElement.jsonObject["data"] ?: JsonObject(emptyMap())
                        val data = json.decodeFromJsonElement<List<OneTimeKey>>(dataObject)
                        val lastId = if (hasMore) data.lastOrNull()?.id else null
                        val date = response.header("date", null) ?: ""
                        val dto = OneTimeKeyResponse(
                            startingAfter = lastId, date = date, data = data
                        )
                        dto
                    }.onSuccess { dto -> it.resume(dto) }
                        .onFailure { ex -> it.resumeWithException(ex) }
                }.onFailure { ex -> it.resumeWithException(ex) }
        }

    override suspend fun getAssets(pageSize: Int, startingAfter: String?): AssetsResponse =
        suspendCancellableCoroutine {
            val url = HttpUrl.Builder()
                .scheme(SCHEME).host(host)
                .addPathSegment("assets").run {
                    if (startingAfter != null) {
                        addEncodedQueryParameter("starting_after", startingAfter)
                    } else this
                }
                .addEncodedQueryParameter("limit", pageSize.toString())
                .build()

            val request: Request = Request.Builder().url(url).get().build()

            val client = OkHttpClient().newBuilder()
                .addInterceptor(HeadersInterceptor(okHttpProvider.tokenProvider))
                .addInterceptor(HttpLoggingInterceptor().apply {
                    redactHeader("Authorization")
                    level = when (BuildConfig.DEBUG) {
                        true -> HttpLoggingInterceptor.Level.HEADERS
                        else -> HttpLoggingInterceptor.Level.NONE
                    }
                }
                )
                .buildFlexaClient()

            runCatching { client.newCall(request).execute() }
                .fold(
                    onSuccess = { response ->
                        runCatching {
                            val raw = response.body?.string().toString()
                            val jsonElement = json.parseToJsonElement(raw)
                            val hasMore = jsonElement.jsonObject["has_more"].toString().toBoolean()
                            val dataObject =
                                jsonElement.jsonObject["data"] ?: JsonObject(emptyMap())
                            val data = json.decodeFromJsonElement<List<Asset>>(dataObject)
                            val lastId = if (hasMore) data.lastOrNull()?.id else null
                            val dto = AssetsResponse(startingAfter = lastId, data = data)
                            dto
                        }.fold(
                            onSuccess = { dto -> it.resume(dto) },
                            onFailure = { ex -> it.resumeWithException(ex) }
                        )
                    },
                    onFailure = { ex -> it.resumeWithException(ex) }
                )

        }

    override suspend fun getAssetById(assetId: String): Asset = suspendCancellableCoroutine {
        val url = HttpUrl.Builder()
            .scheme(SCHEME).host(host)
            .addPathSegment("assets")
            .addEncodedPathSegment(assetId)
            .build()

        val request: Request = Request.Builder().url(url).get().build()

        runCatching { okHttpProvider.client.newCall(request).execute() }
            .fold(
                onSuccess = { response ->
                    runCatching {
                        val raw = response.body?.string().toString()
                        val dto = json.decodeFromString<Asset>(raw)
                        dto
                    }.fold(
                        onSuccess = { dto -> it.resume(dto) },
                        onFailure = { ex -> it.resumeWithException(ex) }
                    )
                },
                onFailure = { ex -> it.resumeWithException(ex) }
            )
    }

    override suspend fun getAccount(): Account = suspendCancellableCoroutine {
        val url = HttpUrl.Builder()
            .scheme(SCHEME).host(host)
            .addPathSegment("accounts")
            .addPathSegment("me")
            .build()

        val request: Request = Request.Builder().url(url).get().build()

        runCatching { okHttpProvider.client.newCall(request).execute() }
            .fold(
                onSuccess = { response ->
                    runCatching {
                        val raw = response.body?.string().toString()
                        json.decodeFromString<Account>(raw)
                    }.fold(
                        onSuccess = { dto -> it.resume(dto) },
                        onFailure = { ex -> it.resumeWithException(ex) }
                    )
                },
                onFailure = { ex -> it.resumeWithException(ex) }
            )
    }

    override suspend fun deleteToken(tokenId: String): Int = suspendCancellableCoroutine {
        val url = HttpUrl.Builder()
            .scheme(SCHEME).host(host)
            .addPathSegment("tokens")
            .addEncodedPathSegment(tokenId)
            .build()

        val request: Request = Request.Builder().url(url).delete().build()

        runCatching { okHttpProvider.client.newCall(request).execute() }
            .fold(
                onSuccess = { response ->
                    runCatching { response.code }.fold(
                        onSuccess = { dto ->
                            okHttpProvider.tokenProvider.dropCache()
                            it.resume(dto)
                        },
                        onFailure = { ex -> it.resumeWithException(ex) }
                    )
                },
                onFailure = { ex -> it.resumeWithException(ex) }
            )
    }

    override suspend fun deleteAccount(): Int = suspendCancellableCoroutine {
        val url = HttpUrl.Builder()
            .scheme(SCHEME).host(host)
            .addPathSegment("accounts")
            .addPathSegment("me")
            .addPathSegment("initiate_deletion")
            .build()

        val request: Request = Request.Builder().url(url)
            .post(EMPTY_REQUEST).build()

        runCatching { okHttpProvider.client.newCall(request).execute() }
            .fold(
                onSuccess = { response ->
                    runCatching { response.code }.fold(
                        onSuccess = { dto -> it.resume(dto) },
                        onFailure = { ex -> it.resumeWithException(ex) }
                    )
                },
                onFailure = { ex -> it.resumeWithException(ex) }
            )
    }

    override suspend fun deleteNotification(id: String): Unit = suspendCancellableCoroutine {
        val url = HttpUrl.Builder()
            .scheme(SCHEME).host(host)
            .addPathSegment("app_notifications")
            .addEncodedPathSegment(id)
            .build()

        val request: Request = Request.Builder().url(url).delete().build()

        runCatching { okHttpProvider.client.newCall(request).execute() }
            .fold(
                onSuccess = { response ->
                    runCatching {
                        response.body?.string().toString()
                    }.fold(
                        onSuccess = { _ -> it.resume(Unit) },
                        onFailure = { ex -> it.resumeWithException(ex) }
                    )
                },
                onFailure = { ex -> it.resumeWithException(ex) }
            )
    }

    override suspend fun listenEvents(lastEventId: String?): Flow<SseEvent> =
        callbackFlow {
            val sessionCreated = "commerce_session.created"
            val sessionRequiresTransaction = "commerce_session.requires_transaction"
            val sessionRequiresApproval = "commerce_session.requires_approval"
            val sessionClosed = "commerce_session.closed"
            val sessionCompleted = "commerce_session.completed"
            val url = HttpUrl.Builder()
                .scheme(SCHEME).host(host)
                .addPathSegment("events")
                .addEncodedQueryParameter(
                    "type",
                    "$sessionCreated," +
                            "$sessionRequiresTransaction," +
                            "$sessionRequiresApproval," +
                            "$sessionClosed," +
                            sessionCompleted
                )
                .build()
            val sseClient = okHttpProvider.sseClient
            val sseRequest = Request.Builder()
                .apply { lastEventId?.let { addHeader("Last-Event-ID", it) } }
                .url(url)
                .build()

            val sseEventSourceListener = object : EventSourceListener() {
                override fun onClosed(eventSource: EventSource) {
                    Log.d("TAG", "onClosed: $eventSource")
                    close()
                }

                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
                    Log.d(null, "onEvent: $data eventSource: $eventSource id: $id, type: $type")
                    when (type) {
                        sessionCreated,
                        sessionRequiresTransaction,
                        sessionRequiresApproval,
                        sessionClosed,
                        sessionCompleted -> {
                            val dto = json.decodeFromString<CommerceSession>(data)
                            trySend(SseEvent.Session(id, dto))
                        }

                        else -> {}
                    }
                    runCatching {
                        val jsonResponse = json.parseToJsonElement(data)
                        val error = jsonResponse.jsonObject["error"]
                        error?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                    }.onSuccess { message ->
                        if (message != null) {
                            cancel(cause = CancellationException(message))
                        }
                    }
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?
                ) {
                    Log.e(null, "onFailure: ", t)
                    cancel(message = response?.message ?: "", cause = t)
                }

                override fun onOpen(eventSource: EventSource, response: Response) {
                    Log.d(null, "onOpen: ${response.code} ${response.message}")
                    if (!response.isSuccessful)
                        cancel(cause = CancellationException())
                }
            }
            val eventSource = EventSources.createFactory(sseClient)
                .newEventSource(request = sseRequest, listener = sseEventSourceListener)
            awaitClose { eventSource.cancel() }
        }

    override suspend fun getBrands(legacyOnly: Boolean?, startingAfter: String?): BrandsResponse =
        suspendCancellableCoroutine {
            var builder = HttpUrl.Builder().scheme(SCHEME).host(host)
                .addPathSegment("brands")
                .addEncodedQueryParameter("limit", "100")
            when (legacyOnly) {
                null -> {}
                true -> {
                    builder = builder
                        .addEncodedQueryParameter("query", "-legacy_flexcodes:null")
                }

                false -> {
                    builder = builder
                        .addEncodedQueryParameter("query", "legacy_flexcodes:null")
                }
            }
            if (startingAfter != null) {
                builder = builder.addEncodedQueryParameter("starting_after", startingAfter)
            }

            val url = builder.build()

            val request: Request = Request.Builder().url(url).get().build()

            val client = OkHttpClient().newBuilder()
                .addInterceptor(HeadersInterceptor(okHttpProvider.tokenProvider))
                .addInterceptor(HttpLoggingInterceptor().apply {
                    redactHeader("Authorization")
                    level = when (BuildConfig.DEBUG) {
                        true -> HttpLoggingInterceptor.Level.HEADERS
                        else -> HttpLoggingInterceptor.Level.NONE
                    }
                }
                )
                .buildFlexaClient()

            runCatching { client.newCall(request).execute() }
                .fold(
                    onSuccess = { response ->
                        runCatching {
                            val raw = response.body?.string().toString()
                            val jsonElement = json.parseToJsonElement(raw)
                            val hasMore = jsonElement.jsonObject["has_more"].toString().toBoolean()
                            val dataObject =
                                jsonElement.jsonObject["data"] ?: JsonObject(emptyMap())
                            val data = json.decodeFromJsonElement<List<Brand>>(dataObject)
                            val lastId = if (hasMore) data.lastOrNull()?.id else null
                            Log.d("TAG", "getBrands loop: hasMore:$hasMore lastId:$lastId")
                            val dto = BrandsResponse(startingAfter = lastId, data = data)
                            dto
                        }.fold(
                            onSuccess = { dto -> it.resume(dto) },
                            onFailure = { ex -> it.resumeWithException(ex) }
                        )
                    },
                    onFailure = { ex -> it.resumeWithException(ex) }
                )
        }

    override suspend fun createCommerceSession(
        brandId: String,
        amount: String,
        assetId: String,
        paymentAssetId: String
    ): CommerceSession.Data = suspendCancellableCoroutine {
        val url = HttpUrl.Builder()
            .scheme(SCHEME).host(host)
            .addPathSegment("commerce_sessions")
            .build()

        val body = buildJsonObject {
            put("brand", brandId)
            put("amount", amount)
            put("asset", assetId)
            val preferences = buildJsonObject { put("payment_asset", paymentAssetId) }
            put("preferences", preferences)
        }.run { toString().toRequestBody(mediaType) }

        val request: Request = Request.Builder().url(url)
            .post(body).build()

        runCatching { okHttpProvider.client.newCall(request).execute() }
            .onSuccess { response ->
                if (response.isSuccessful) {
                    runCatching {
                        val raw = response.body?.string().toString()
                        json.decodeFromString<CommerceSession.Data>(raw)
                    }.onSuccess { dto -> it.resume(dto) }
                        .onFailure { e ->
                            Log.e(null, "createCommerceSession: ", e)
                            it.resumeWithException(
                                ApiException(
                                    message = "Can't parse the commerce session data",
                                    traceId = response.header("client-trace-id")
                                )
                            )
                        }
                } else {
                    val e = response.toApiException()
                    it.resumeWithException(e)
                }
            }
            .onFailure { ex ->
                it.resumeWithException(ex)
            }
    }

    override suspend fun closeCommerceSession(commerceSessionId: String): CommerceSession.Data =
        suspendCancellableCoroutine {
            val url = HttpUrl.Builder()
                .scheme(SCHEME).host(host)
                .addPathSegment("commerce_sessions")
                .addEncodedPathSegment(commerceSessionId)
                .addPathSegment("close")
                .build()

            val request: Request = Request.Builder().url(url)
                .post(EMPTY_REQUEST).build()

            runCatching { okHttpProvider.client.newCall(request).execute() }
                .fold(
                    onSuccess = { response ->
                        if (response.isSuccessful) {
                            runCatching {
                                val raw = response.body?.string().toString()
                                json.decodeFromString<CommerceSession.Data>(raw)
                            }.onSuccess { dto -> it.resume(dto) }
                                .onFailure { e ->
                                    Log.e(null, "closeCommerceSession: ", e)
                                    it.resumeWithException(
                                        ApiException(
                                            message = "Can't parse the commerce session data",
                                            traceId = response.header("client-trace-id")
                                        )
                                    )
                                }
                        } else {
                            val e = response.toApiException()
                            it.resumeWithException(e)
                        }
                    },
                    onFailure = { ex -> it.resumeWithException(ex) }
                )
        }

    override suspend fun confirmTransaction(
        commerceSessionId: String,
        txSignature: String
    ): String = suspendCancellableCoroutine {
        val url = HttpUrl.Builder()
            .scheme(SCHEME).host(host)
            .addPathSegment("transactions")
            .addEncodedPathSegment(commerceSessionId)
            .build()

        val body = buildJsonObject {
            put("signature", txSignature)
        }.run { toString().toRequestBody(mediaType) }

        val request: Request = Request.Builder().url(url)
            .patch(body).build()

        runCatching { okHttpProvider.client.newCall(request).execute() }
            .fold(
                onSuccess = { response ->
                    runCatching {
                        val raw = response.body?.string().toString()
                        raw
                    }.fold(
                        onSuccess = { res -> it.resume(res) },
                        onFailure = { ex -> it.resumeWithException(ex) }
                    )
                },
                onFailure = { ex -> it.resumeWithException(ex) }
            )
    }

    override suspend fun patchCommerceSession(
        commerceSessionId: String,
        paymentAssetId: String
    ): String =
        suspendCancellableCoroutine {
            val url = HttpUrl.Builder()
                .scheme(SCHEME).host(host)
                .addPathSegment("commerce_sessions")
                .addEncodedPathSegment(commerceSessionId)
                .build()

            val body = buildJsonObject {
                val preferences = buildJsonObject {
                    put("payment_asset", paymentAssetId)
                }
                put("preferences", preferences)
            }.run { toString().toRequestBody(mediaType) }

            val request: Request = Request.Builder().url(url)
                .patch(body).build()

            runCatching { okHttpProvider.client.newCall(request).execute() }
                .onFailure { ex -> it.resumeWithException(ex) }
                .onSuccess { response ->
                    when {
                        response.code == HttpURLConnection.HTTP_OK -> {
                            val raw = response.body?.string().toString()
                            it.resume(raw)
                        }

                        else -> {
                            val apiException = response.toApiException()
                            it.resumeWithException(apiException)
                        }
                    }
                }
        }

    override suspend fun approveCommerceSession(commerceSessionId: String): Int =
        suspendCancellableCoroutine {
            val url = HttpUrl.Builder()
                .scheme(SCHEME).host(host)
                .addPathSegment("commerce_sessions")
                .addEncodedPathSegment(commerceSessionId)
                .addPathSegment("approve")
                .build()

            val request: Request = Request.Builder().url(url).post(EMPTY_REQUEST).build()

            runCatching { okHttpProvider.client.newCall(request).execute() }
                .onSuccess { response ->
                    if (response.isSuccessful) {
                        it.resume(response.code)
                    } else {
                        val e = response.toApiException()
                        it.resumeWithException(e)
                    }
                }
                .onFailure { ex -> it.resumeWithException(ex) }
        }

    override suspend fun getCommerceSession(sessionId: String): CommerceSession.Data =
        suspendCancellableCoroutine {
            val url = HttpUrl.Builder()
                .scheme(SCHEME).host(host)
                .addPathSegment("commerce_sessions")
                .addEncodedPathSegment(sessionId)
                .build()

            val request: Request = Request.Builder().url(url).get().build()

            runCatching { okHttpProvider.client.newCall(request).execute() }
                .fold(
                    onSuccess = { response ->
                        runCatching {
                            val res = response.body?.string().toString()
                            if (response.code != HttpURLConnection.HTTP_OK) {
                                Result.failure(NullPointerException())
                            } else {
                                val dto = json.decodeFromString<CommerceSession.Data>(res)
                                Result.success(dto)
                            }
                        }.fold(
                            onSuccess = { res ->
                                if (res.isSuccess) {
                                    res.getOrNull()?.let { r -> it.resume(r) }
                                } else {
                                    res.exceptionOrNull()?.let { e ->
                                        it.resumeWithException(e)
                                    }
                                }
                            },
                            onFailure = { ex -> it.resumeWithException(ex) }
                        )
                    },
                    onFailure = { ex -> it.resumeWithException(ex) }
                )
        }

    override suspend fun getExchangeRates(
        assetIds: List<String>,
        unitOfAccount: String
    ): ExchangeRatesResponse = suspendCancellableCoroutine {
        val ids = assetIds.joinToString(",")
        val url = HttpUrl.Builder()
            .scheme(SCHEME).host(host)
            .addPathSegment("exchange_rates")
            .addEncodedQueryParameter("assets", ids)
            .addEncodedQueryParameter("unit_of_account", unitOfAccount)
            .build()

        val request: Request = Request.Builder().url(url)
            .get().build()

        runCatching { okHttpProvider.client.newCall(request).execute() }
            .onSuccess { response ->
                runCatching {
                    if (response.code != HttpURLConnection.HTTP_OK) {
                        Result.failure(NullPointerException())
                    } else {
                        val raw = response.body?.string().toString()
                        val jsonElement = json.parseToJsonElement(raw)
                        val data = jsonElement.jsonObject["data"]
                        val date = response.header("date", null) ?: ""
                        val dto = data?.let { json.decodeFromJsonElement<List<ExchangeRate>>(it) }
                            ?: emptyList()
                        Result.success(ExchangeRatesResponse(date = date, data = dto))
                    }
                }.onSuccess { res ->
                    if (res.isSuccess) {
                        res.getOrNull()?.let { r -> it.resume(r) }
                    } else {
                        res.exceptionOrNull()?.let { e ->
                            it.resumeWithException(e)
                        }
                    }
                }.onFailure { ex -> it.resumeWithException(ex) }
            }.onFailure { ex -> it.resumeWithException(ex) }
    }

    override suspend fun getTransactionFees(assetIds: List<String>): List<TransactionFee> =
        suspendCancellableCoroutine {
            val ids = assetIds.joinToString(",")
            val url = HttpUrl.Builder()
                .scheme(SCHEME).host(host)
                .addPathSegment("transaction_fees")
                .addEncodedQueryParameter("transaction_assets", ids)
                .build()

            val request: Request = Request.Builder().url(url)
                .get().build()

            runCatching { okHttpProvider.client.newCall(request).execute() }
                .onSuccess { response ->
                    runCatching {
                        if (response.code != HttpURLConnection.HTTP_OK) {
                            Result.failure(NullPointerException())
                        } else {
                            val raw = response.body?.string().toString()
                            val jsonElement = json.parseToJsonElement(raw)
                            val data = jsonElement.jsonObject["data"]
                            val dto =
                                data?.let { json.decodeFromJsonElement<List<TransactionFee>>(it) }
                                    ?: emptyList()
                            Result.success(dto)
                        }
                    }.onSuccess { res ->
                        if (res.isSuccess) {
                            res.getOrNull()?.let { r -> it.resume(r) }
                        } else {
                            res.exceptionOrNull()?.let { e ->
                                it.resumeWithException(e)
                            }
                        }
                    }.onFailure { ex -> it.resumeWithException(ex) }
                }.onFailure { ex -> it.resumeWithException(ex) }
        }
}
