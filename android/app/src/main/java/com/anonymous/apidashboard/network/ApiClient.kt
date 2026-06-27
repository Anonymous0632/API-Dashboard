package com.anonymous.apidashboard.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiClient {
    private val client = OkHttpClient.Builder()
        .callTimeout(6, TimeUnit.SECONDS)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun getJson(url: String, headers: Map<String, String> = emptyMap()): HttpJson {
        return execute(Request.Builder().url(url).headers(headers).get().build())
    }

    suspend fun postJson(url: String, body: JSONObject, headers: Map<String, String> = emptyMap()): HttpJson {
        val requestBody = body.toString().toRequestBody("application/json".toMediaType())
        return execute(Request.Builder().url(url).headers(headers).post(requestBody).build())
    }

    suspend fun postForm(url: String, fields: Map<String, String>, headers: Map<String, String> = emptyMap()): HttpJson {
        val form = FormBody.Builder().apply {
            fields.forEach { (key, value) -> add(key, value) }
        }.build()
        return execute(Request.Builder().url(url).headers(headers).post(form).build())
    }

    private suspend fun execute(request: Request): HttpJson {
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val raw = response.body.string()
                val json = runCatching { JSONObject(raw) }.getOrElse {
                    JSONObject().put("raw", raw)
                }
                HttpJson(response.code, json)
            }
        }
    }

    private fun Request.Builder.headers(headers: Map<String, String>): Request.Builder {
        headers.forEach { (key, value) ->
            if (value.isNotEmpty()) header(key, value)
        }
        return this
    }
}

data class HttpJson(
    val status: Int,
    val json: JSONObject,
)
