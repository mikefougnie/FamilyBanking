package com.godskingdomnow.familybank.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/* Messages exchanged with the website's family/bank_app_api.php (see that file for the rules). */

@Serializable
data class UserDto(
    val username: String = "",
    @SerialName("display_name") val displayName: String = "",
    val role: String = "",
    @SerialName("family_name") val familyName: String = "",
)

@Serializable
data class LoginReq(val username: String, val password: String, val device: String)

@Serializable
data class LoginRes(val ok: Boolean = false, val error: String? = null, val token: String? = null, val user: UserDto? = null)

@Serializable
data class SimpleRes(val ok: Boolean = false, val error: String? = null, val user: UserDto? = null)

@Serializable
data class KidDto(
    val cid: String? = null,
    val rid: Long? = null,
    val name: String = "",
    val deleted: Int = 0,
)

@Serializable
data class EntryDto(
    val cid: String? = null,
    val rid: Long? = null,
    @SerialName("kid_rid") val kidRid: Long? = null,
    @SerialName("kid_cid") val kidCid: String? = null,
    val type: String = "deposit",          // deposit | withdraw
    val amount: Double = 0.0,
    val note: String = "",
    val at: Long = 0,                      // milliseconds
    val who: String = "",
    val deleted: Int = 0,
)

@Serializable
data class SyncReq(val kids: List<KidDto>, val entries: List<EntryDto>)

@Serializable
data class IdMapDto(val kids: Map<String, Long> = emptyMap(), val entries: Map<String, Long> = emptyMap())

@Serializable
data class SyncRes(
    val ok: Boolean = false,
    val error: String? = null,
    @SerialName("read_only") val readOnly: Int = 0,
    val skipped: Int = 0,
    val map: IdMapDto = IdMapDto(),
    val kids: List<KidDto> = emptyList(),
    val entries: List<EntryDto> = emptyList(),
    val user: UserDto? = null,
)

/** Thrown when the website says this phone isn't signed in any more. */
class SignedOutException(message: String) : IOException(message)

class SyncApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private suspend fun <Req, Res> post(
        baseUrl: String, action: String, token: String?,
        body: Req, reqSer: KSerializer<Req>, resSer: KSerializer<Res>,
    ): Res = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/bank_app_api.php?action=" + action
        val parsed = url.toHttpUrlOrNull() ?: throw IOException("The website address isn’t valid.")
        val b = Request.Builder().url(parsed).post(json.encodeToString(reqSer, body).toRequestBody(jsonType))
            .header("X-Family-App", "1")
            .header("Origin", "${parsed.scheme}://${parsed.host}")   // the site refuses writes from other sites
        if (!token.isNullOrEmpty()) {
            b.header("Authorization", "Bearer $token")
            b.header("X-App-Token", token)   // some hosts drop the Authorization header
        }
        client.newCall(b.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.code == 401 && !token.isNullOrEmpty()) {
                val msg = runCatching { json.decodeFromString(SimpleRes.serializer(), text).error }.getOrNull()
                throw SignedOutException(msg ?: "This phone was signed out.")
            }
            if (resp.code == 404) throw IOException("The website doesn’t have the Family Bank app update yet (bank_app_api.php).")
            try {
                json.decodeFromString(resSer, text)
            } catch (e: Exception) {
                throw IOException(if (resp.isSuccessful) "The website sent something unexpected." else "The website answered ${resp.code}.")
            }
        }
    }

    suspend fun login(baseUrl: String, username: String, password: String, device: String): LoginRes =
        post(baseUrl, "login", null, LoginReq(username, password, device), LoginReq.serializer(), LoginRes.serializer())

    suspend fun logout(baseUrl: String, token: String): SimpleRes =
        post(baseUrl, "logout", token, SimpleRes(true), SimpleRes.serializer(), SimpleRes.serializer())

    suspend fun sync(baseUrl: String, token: String, req: SyncReq): SyncRes =
        post(baseUrl, "sync", token, req, SyncReq.serializer(), SyncRes.serializer())
}
