package com.stremio.mobile.data.repository

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

sealed interface StremioLinkReadResult {
    data object Pending : StremioLinkReadResult
    data class Authorized(val authKey: String) : StremioLinkReadResult
    data class ExpiredOrError(val message: String) : StremioLinkReadResult
}

class StremioLinkRepository {
    suspend fun createLink(): StremioAccountLink = withContext(Dispatchers.IO) {
        val result = request("/create?type=Create")
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

    suspend fun readLink(code: String): StremioLinkReadResult = withContext(Dispatchers.IO) {
        try {
            val encodedCode = URLEncoder.encode(code, Charsets.UTF_8.name())
            val response = request("/read?type=Read&code=$encodedCode")
            val result = response.optJSONObject("result")
            val authKey = result?.optString("authKey")?.trim().orEmpty()
            if (authKey.isNotEmpty()) StremioLinkReadResult.Authorized(authKey)
            else StremioLinkReadResult.ExpiredOrError("Stremio returned an invalid authorization response")
        } catch (error: StremioLinkApiException) {
            if (error.apiCode == PENDING_API_CODE) StremioLinkReadResult.Pending
            else StremioLinkReadResult.ExpiredOrError(error.message ?: "Unable to check the link")
        } catch (error: StremioLinkException) {
            StremioLinkReadResult.ExpiredOrError(error.message ?: "Unable to check the link")
        }
    }

    private fun request(path: String): JSONObject {
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
                ?: throw StremioLinkException("Stremio link service returned no response")
            val json = runCatching { JSONObject(body) }
                .getOrElse { throw StremioLinkException("Stremio link service returned an invalid response") }
            val errorValue = json.opt("error")
            val error = json.optJSONObject("error")
            val hasApiError = errorValue != null && errorValue != JSONObject.NULL && errorValue != false
            if (status !in 200..299 || hasApiError) {
                val code = error?.optInt("code", json.optInt("code", -1)) ?: json.optInt("code", -1)
                val message = error?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: (errorValue as? String)?.takeIf { it.isNotBlank() }
                    ?: json.optString("message").takeIf { it.isNotBlank() }
                    ?: "Stremio link request failed"
                throw StremioLinkApiException(code, message)
            }
            return json
        } catch (error: StremioLinkException) {
            throw error
        } catch (_: Exception) {
            throw StremioLinkException("Unable to reach Stremio's link service")
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val BASE_URL = "https://link.stremio.com/api/v2"
        const val PENDING_API_CODE = 101
    }
}

private open class StremioLinkException(message: String) : Exception(message)
private class StremioLinkApiException(val apiCode: Int, message: String) : StremioLinkException(message)
