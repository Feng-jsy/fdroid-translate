package org.fdroid.ui.details

import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Result of a translation attempt.
 */
sealed interface TranslationResult {
  data class Success(val text: String) : TranslationResult

  /** Translation failed on all engines. [message] is a technical detail for logging. */
  data class Error(val message: String) : TranslationResult
}

/**
 * Translates app descriptions with a three-engine fallback chain:
 *
 * 1. Tencent Transmart - reachable from mainland China without a proxy, no API key,
 *    no daily quota, and it accepts multiple paragraphs in a single request.
 * 2. Google Translate free endpoint (client=gtx) - best quality, needs Google reachable.
 * 3. MyMemory free API - last resort, anonymous quota is small (~5000 chars/day per IP).
 *
 * Results are cached in memory so toggling original/translated or re-opening the same app
 * never hits the network twice.
 */
object DescriptionTranslator {

  private val client =
    OkHttpClient.Builder()
      .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
      .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
      .build()

  private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

  /** Cache key: target language + text hash, so the same description is translated only once. */
  private val cache = ConcurrentHashMap<String, String>()

  /** Max characters per request chunk (all three endpoints reject very long payloads). */
  private const val MAX_CHUNK_LENGTH = 1800

  /**
   * Translates [text] to [targetLanguage] (BCP-47 code, e.g. "zh-CN", "en").
   * The text is split at paragraph boundaries and sent as a batch where the engine allows it.
   */
  suspend fun translate(text: String, targetLanguage: String): TranslationResult =
    withContext(Dispatchers.IO) {
      val cacheKey = "$targetLanguage|${text.hashCode()}"
      cache[cacheKey]?.let { return@withContext TranslationResult.Success(it) }

      val chunks = splitIntoChunks(text)
      val translated =
        translateWithTencent(chunks, targetLanguage)
          ?: translateWithGoogle(chunks, targetLanguage)
          ?: translateWithMyMemory(chunks, targetLanguage)
          ?: return@withContext TranslationResult.Error("All translation engines failed")

      cache[cacheKey] = translated
      TranslationResult.Success(translated)
    }

  /** Tencent Transmart: POSTs all chunks at once and gets them back in order. */
  private fun translateWithTencent(chunks: List<String>, targetLanguage: String): String? {
    return try {
      val textArray = JSONArray()
      chunks.forEach { textArray.put(it) }
      val body =
        JSONObject()
          .put(
            "header",
            JSONObject().put("fn", "auto_translation").put("client_key", TENCENT_CLIENT_KEY),
          )
          .put("type", "plain")
          .put("model_category", "normal")
          .put("text_domain", "")
          .put("source", JSONObject().put("lang", "auto").put("text_list", textArray))
          .put("target", JSONObject().put("lang", toTencentLanguage(targetLanguage)))
          .toString()
      val request =
        Request.Builder()
          .url("https://transmart.qq.com/api/imt")
          .header("Content-Type", "application/json")
          .header("Referer", "https://transmart.qq.com/zh-CN/index")
          .header("User-Agent", USER_AGENT)
          .post(body.toRequestBody(JSON_MEDIA_TYPE))
          .build()
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return null
        val raw = response.body?.string() ?: return null
        val json = JSONObject(raw)
        if (json.optJSONObject("header")?.optString("ret_code") != "succ") return null
        val result = json.optJSONArray("auto_translation") ?: return null
        buildString {
          for (i in 0 until result.length()) {
            if (i > 0) append('\n')
            append(result.optString(i))
          }
        }.ifBlank { null }
      }
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Google Translate free web endpoint, one request per chunk. No API key needed.
   * Returns null on any failure (network, parsing, rate limit).
   */
  private fun translateWithGoogle(chunks: List<String>, targetLanguage: String): String? {
    val result = StringBuilder()
    for ((index, chunk) in chunks.withIndex()) {
      val translated = translateChunkWithGoogle(chunk, targetLanguage) ?: return null
      if (index > 0) result.append('\n')
      result.append(translated)
    }
    return result.toString()
  }

  private fun translateChunkWithGoogle(text: String, targetLanguage: String): String? {
    return try {
      val encoded = URLEncoder.encode(text, "UTF-8")
      val url =
        "https://translate.googleapis.com/translate_a/single" +
          "?client=gtx&sl=auto&tl=$targetLanguage&dt=t&q=$encoded"
      val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return null
        val body = response.body?.string() ?: return null
        // Response shape: [[["translated","original",...],...],...]
        val segments = JSONArray(body).getJSONArray(0)
        buildString {
          for (i in 0 until segments.length()) {
            append(segments.getJSONArray(i).optString(0))
          }
        }.ifBlank { null }
      }
    } catch (e: Exception) {
      null
    }
  }

  /**
   * MyMemory free API, last resort. Anonymous quota is roughly 5000 chars/day per IP.
   * The free API requires an explicit source language, so we assume English - F-Droid
   * descriptions that need translation are overwhelmingly English.
   */
  private fun translateWithMyMemory(chunks: List<String>, targetLanguage: String): String? {
    val result = StringBuilder()
    for ((index, chunk) in chunks.withIndex()) {
      val translated = translateChunkWithMyMemory(chunk, targetLanguage) ?: return null
      if (index > 0) result.append('\n')
      result.append(translated)
    }
    return result.toString()
  }

  private fun translateChunkWithMyMemory(text: String, targetLanguage: String): String? {
    return try {
      val encoded = URLEncoder.encode(text, "UTF-8")
      val url = "https://api.mymemory.translated.net/get?q=$encoded&langpair=en|$targetLanguage"
      val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return null
        val body = response.body?.string() ?: return null
        val json = JSONObject(body)
        val translated = json.getJSONObject("responseData").getString("translatedText")
        // MyMemory signals quota exhaustion inside a 200 response
        if (
          translated.contains("MYMEMORY WARNING", ignoreCase = true) ||
            translated.contains("QUERY LENGTH LIMIT", ignoreCase = true)
        ) {
          null
        } else {
          translated.ifBlank { null }
        }
      }
    } catch (e: Exception) {
      null
    }
  }

  /** Tencent uses short language codes, e.g. "zh" instead of "zh-CN". */
  private fun toTencentLanguage(targetLanguage: String): String =
    if (targetLanguage.startsWith("zh")) "zh" else targetLanguage.substringBefore('-')

  /** Splits text at paragraph boundaries into chunks of at most [MAX_CHUNK_LENGTH]. */
  private fun splitIntoChunks(text: String): List<String> {
    val paragraphs = text.split('\n')
    val chunks = mutableListOf<String>()
    val current = StringBuilder()
    for (paragraph in paragraphs) {
      if (current.length + paragraph.length + 1 > MAX_CHUNK_LENGTH && current.isNotEmpty()) {
        chunks.add(current.toString())
        current.clear()
      }
      if (current.isNotEmpty()) current.append('\n')
      current.append(paragraph)
    }
    if (current.isNotEmpty()) chunks.add(current.toString())
    return chunks
  }

  /**
   * Cheap heuristic: returns true when the text looks like it is already written in the
   * system language, in which case we hide the translate button.
   */
  fun isAlreadyInTargetLanguage(text: String, targetLanguage: String): Boolean {
    if (!targetLanguage.startsWith("zh")) return false
    val cjkCount = text.count { it in '\u4e00'..'\u9fff' }
    return cjkCount > text.length / 4
  }

  /** client_key is required by the endpoint but not validated, any browser-style id works. */
  private const val TENCENT_CLIENT_KEY =
    "browser-chrome-120.0.0-MacOS-00000000-0000-0000-0000-000000000000-1700000000000"

  private const val USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 " +
      "Mobile Safari/537.36"
}
