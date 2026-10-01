package com.stremio.mobile.data.repository

import android.util.Log
import com.stremio.mobile.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class StremioAccountLink(
    val code: String,
    val link: String,
    val qrcode: String,
)

internal interface StremioAccountLinkDataSource {
    suspend fun createLink(): StremioAccountLink
    suspend fun readLink(code: String): StremioLinkReadResult
    suspend fun checkLinkValidity(link: String): StremioLinkValidity
}

sealed interface StremioLinkValidity {
    data object Active : StremioLinkValidity
    data object Expired : StremioLinkValidity
    data class Unknown(val message: String) : StremioLinkValidity
}

sealed interface StremioLinkReadResult {
    data object Pending : StremioLinkReadResult
    data class Authorized(val authKey: String) : StremioLinkReadResult
    data class ApiError(val code: Int?, val message: String) : StremioLinkReadResult
    data class TransportError(val message: String) : StremioLinkReadResult
}

class StremioLinkRepository : StremioAccountLinkDataSource {
    override suspend fun createLink(): StremioAccountLink = withContext(Dispatchers.IO) {
        val result = request("/create?type=Create", endpoint = Endpoint.Create).json
        val payload = result.optJSONObject("result")
            ?: throw StremioLinkException("Stremio returned an invalid link response")
        val code = payload.optString("code").trim()
        val link = payload.optString("link").trim()
        val qrcode = payload.optString("qrcode").trim()
        if (code.length != 4 || link.isBlank() || qrcode.isBlank()) {
            throw StremioLinkException("Stremio returned an invalid link response")
        }
        StremioAccountLink(code = code, link = link, qrcode = qrcode)
    }

    override suspend fun readLink(code: String): StremioLinkReadResult = withContext(Dispatchers.IO) {
        try {
            val encodedCode = URLEncoder.encode(code, Charsets.UTF_8.name())
            val response = request("/read?type=Read&code=$encodedCode", endpoint = Endpoint.Read)
            val result = response.json.optJSONObject("result")
            val authKey = result?.optString("authKey")?.trim().orEmpty()
            logReadResponse(
                status = response.status,
                hasResult = result != null,
                hasAuthKey = authKey.isNotEmpty(),
                apiCode = null,
                message = null,
                requestedCode = code,
                authKey = authKey,
            )
            classifyLinkReadResponse(authKey = authKey, apiCode = null)
        } catch (error: StremioLinkApiException) {
            logReadResponse(error.status, error.hasResult, error.authKey != null, error.apiCode, error.message, code, error.authKey)
            classifyLinkReadApiError(
                code = error.apiCode,
                message = error.message ?: "Unable to check the link",
            )
        } catch (error: StremioLinkException) {
            logReadResponse(error.status, error.hasResult, error.authKey != null, null, error.message, code, error.authKey)
            StremioLinkReadResult.TransportError(error.message ?: "Unable to check the link")
        }
    }

    override suspend fun checkLinkValidity(link: String): StremioLinkValidity = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(link).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "text/html")
            }
            val status = connection.responseCode
            val locationPresent = !connection.getHeaderField("Location").isNullOrBlank()
            val html = if (status == HttpURLConnection.HTTP_OK) {
                connection.inputStream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                    val htmlBuilder = StringBuilder(MAX_EXPIRY_HTML_CHARS)
                    val buffer = CharArray(2_048)
                    while (htmlBuilder.length < MAX_EXPIRY_HTML_CHARS) {
                        val count = reader.read(buffer, 0, minOf(buffer.size, MAX_EXPIRY_HTML_CHARS - htmlBuilder.length))
                        if (count < 0) break
                        htmlBuilder.append(buffer, 0, count)
                    }
                    htmlBuilder.toString()
                }
            } else null
            val validity = classifyLinkValidity(status, locationPresent, html)
            if (BuildConfig.DEBUG) {
                val classification = when (validity) {
                    StremioLinkValidity.Active -> "active"
                    StremioLinkValidity.Expired -> "expired"
                    is StremioLinkValidity.Unknown -> "unknown"
                }
                Log.d(TAG, "StremioLink validity status=$status classification=$classification")
            }
            validity
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            StremioLinkValidity.Unknown("Unable to check this link")
        } finally {
            connection?.disconnect()
        }
    }

    private fun request(path: String, endpoint: Endpoint): LinkHttpResponse {
        val connection = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?: throw StremioLinkException("Stremio link service returned no response", status)
            val json = runCatching { JSONObject(body) }
                .getOrElse { throw StremioLinkException("Stremio link service returned an invalid response", status) }
            if (endpoint == Endpoint.Create) logCreateResponse(status, json)
            val errorValue = json.opt("error")
            val error = json.optJSONObject("error")
            val hasApiError = errorValue != null && errorValue != JSONObject.NULL && errorValue != false
            if (status !in 200..299 || hasApiError) {
                val result = json.optJSONObject("result")
                val authKey = result?.optString("authKey")?.takeIf { it.isNotBlank() }
                val code = (error?.takeIf { it.has("code") }?.opt("code") ?: json.opt("code"))
                    ?.let { value -> value.toString().toIntOrNull() }
                val message = error?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: (errorValue as? String)?.takeIf { it.isNotBlank() }
                    ?: json.optString("message").takeIf { it.isNotBlank() }
                    ?: "Stremio link request failed"
                throw StremioLinkApiException(status, json.has("result"), authKey, code, message)
            }
            return LinkHttpResponse(status, json)
        } catch (error: StremioLinkException) {
            throw error
        } catch (_: Exception) {
            throw StremioLinkException("Unable to reach Stremio's link service")
        } finally {
            connection.disconnect()
        }
    }

    private fun logCreateResponse(status: Int, json: JSONObject) {
        if (!BuildConfig.DEBUG) return
        val result = json.optJSONObject("result")
        Log.d(TAG, "StremioLink create status=$status topKeys=${json.keys().asSequence().toList()} resultKeys=${result?.keys()?.asSequence()?.toList() ?: emptyList<String>()}")
        listOfNotNull(json, result).forEach { objectWithMetadata ->
            objectWithMetadata.keys().forEach { key ->
                if (key.lowercase() in LIFETIME_FIELDS) {
                    val value = objectWithMetadata.opt(key)
                    if (value is String || value is Number || value is Boolean) {
                        Log.d(TAG, "StremioLink create metadata $key=${sanitize(value.toString())}")
                    }
                }
            }
        }
    }

    private fun logReadResponse(
        status: Int?, hasResult: Boolean, hasAuthKey: Boolean, apiCode: Int?, message: String?,
        requestedCode: String, authKey: String?,
    ) {
        if (!BuildConfig.DEBUG) return
        val safeMessage = message?.let { sanitize(it, requestedCode, authKey) }
        Log.d(TAG, "StremioLink read status=${status ?: "none"} hasResult=$hasResult hasAuthKey=$hasAuthKey apiCode=${apiCode ?: "none"} message=${safeMessage?.let { "\"$it\"" } ?: "none"}")
    }

    private fun sanitize(value: String, requestedCode: String? = null, authKey: String? = null): String = value
        .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), "[redacted-url]")
        .let { safe -> requestedCode?.takeIf(String::isNotBlank)?.let { safe.replace(it, "[redacted-code]", ignoreCase = true) } ?: safe }
        .let { safe -> authKey?.takeIf(String::isNotBlank)?.let { safe.replace(it, "[redacted-auth-key]") } ?: safe }
        .replace(Regex("(?i)bearer\\s+[^\\s,;]+"), "Bearer [redacted]")
        .replace(Regex("\\b[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b"), "[redacted-token]")
        .replace(Regex("(?i)(authkey|token|password|credential)\\s*[:=]\\s*[^\\s,;]+"), "${'$'}1=[redacted]")
        .take(300)

    private enum class Endpoint { Create, Read }
    private data class LinkHttpResponse(val status: Int, val json: JSONObject)

    private companion object {
        const val BASE_URL = "https://link.stremio.com/api/v2"
        const val TAG = "StremioLink"
        val LIFETIME_FIELDS = setOf("expires", "expiresat", "expiresin", "ttl", "createdat")
        const val MAX_EXPIRY_HTML_CHARS = 16_384
    }
}

/** Expiry comes only from the server-rendered link page; CREATE declares no TTL. */
internal fun classifyLinkValidity(
    status: Int,
    locationPresent: Boolean,
    html: String?,
): StremioLinkValidity = when {
    status in REDIRECT_STATUSES && locationPresent -> StremioLinkValidity.Active
    status == HttpURLConnection.HTTP_OK && html.isOfficialExpiredLinkPage() -> StremioLinkValidity.Expired
    else -> StremioLinkValidity.Unknown("Unable to check this link")
}

private fun String?.isOfficialExpiredLinkPage(): Boolean {
    if (this == null || !contains("<html", ignoreCase = true) || !contains("stremio", ignoreCase = true)) return false
    val title = Regex("<title\\b[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .find(this)?.groupValues?.getOrNull(1)?.replace(Regex("\\s+"), " ")?.trim()
    val heading = Regex("<h[1-3]\\b[^>]*>(.*?)</h[1-3]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .findAll(this).map { it.groupValues[1].replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim() }
        .any { it.equals("Code Expired", ignoreCase = true) }
    return title?.equals("Code Expired - Stremio Link", ignoreCase = true) == true && heading
}

private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)

internal fun classifyLinkReadResponse(
    authKey: String = "",
    apiCode: Int?,
    message: String = "Unable to check the link",
): StremioLinkReadResult = when {
    !authKey.isBlank() -> StremioLinkReadResult.Authorized(authKey)
    apiCode != null -> classifyLinkReadApiError(apiCode, message)
    else -> StremioLinkReadResult.TransportError(message)
}

internal fun classifyLinkReadApiError(code: Int?, message: String): StremioLinkReadResult =
    if (code == 101) StremioLinkReadResult.Pending else StremioLinkReadResult.ApiError(code, message)

private open class StremioLinkException(
    message: String,
    val status: Int? = null,
    val hasResult: Boolean = false,
    val authKey: String? = null,
) : Exception(message)
private class StremioLinkApiException(
    status: Int,
    hasResult: Boolean,
    authKey: String?,
    val apiCode: Int?,
    message: String,
) : StremioLinkException(message, status, hasResult, authKey)
