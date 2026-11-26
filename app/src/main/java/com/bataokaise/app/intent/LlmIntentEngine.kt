package com.bataokaise.app.intent

import com.bataokaise.app.voice.Normalized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Optional LLM intent detection through our backend (/intent). The LLM may ONLY return
 * {"intent": "...", "details": {...}}; it never writes journey steps. Any failure falls
 * back to the rule engine, so the app keeps working offline.
 * Status: written, untested without a backend (Demo Mode uses rules).
 */
class LlmIntentEngine(private val backendUrl: String) : IntentEngine {
    override val label = "LLM via backend (rules fallback)"
    private val rules = RuleIntentEngine()
    private val http = OkHttpClient.Builder().callTimeout(4, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun detect(n: Normalized): Intent {
        val ruled = rules.detectSync(n)
        // Short commands and yes/no never need the network.
        if (ruled is Intent.Cmd || ruled == Intent.Yes || ruled == Intent.No || ruled == Intent.Noise) return ruled
        return runCatching { remote(n.raw) }.getOrNull() ?: ruled
    }

    private suspend fun remote(text: String): Intent? = withContext(Dispatchers.IO) {
        val body = """{"text":${Json.encodeToString(kotlinx.serialization.serializer<String>(), text)}}"""
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(backendUrl.trimEnd('/') + "/intent").post(body).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val obj = json.parseToJsonElement(resp.body?.string() ?: return@withContext null).jsonObject
            fromJson(obj)
        }
    }

    companion object {
        fun fromJson(obj: JsonObject): Intent? {
            val d = obj["details"]?.jsonObject
            fun s(k: String) = d?.get(k)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            fun l(k: String) = d?.get(k)?.jsonPrimitive?.longOrNull
            return when (obj["intent"]?.jsonPrimitive?.contentOrNull) {
                "bill_pay" -> Intent.BillPay()
                "udhaar_add" -> Intent.UdhaarAdd(s("name"), l("amount"))
                "udhaar_repay" -> Intent.UdhaarRepay(s("name"), l("amount"))
                "udhaar_balance" -> Intent.UdhaarBalance
                "udhaar_remind" -> Intent.UdhaarRemind(s("name"))
                "schemes" -> Intent.Schemes
                "stuck" -> Intent.Stuck
                "unknown" -> Intent.Unknown
                else -> null
            }
        }
    }
}
