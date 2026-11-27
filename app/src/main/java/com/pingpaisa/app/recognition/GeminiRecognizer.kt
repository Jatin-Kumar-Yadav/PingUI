package com.pingpaisa.app.recognition

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

data class AiProduct(val name: String?, val brand: String?, val category: String?, val subcategory: String?)

/**
 * OPTIONAL online product identification via the Gemini REST API. Active only when GEMINI_API_KEY is set in
 * local.properties. It only suggests a name/brand and a (category, subcategory) chosen from YOUR dataset's
 * taxonomy; the price always stays an OCR/manual value. Any failure returns null and the on-device result is used.
 * Note: a key compiled into an APK can be extracted - for a Play Store release call this through your own backend.
 */
class GeminiRecognizer(private val apiKey: String, private val model: String, private val taxonomy: String) {
    val enabled: Boolean get() = apiKey.isNotBlank()

    suspend fun identify(bitmap: Bitmap, ocrText: String? = null): AiProduct? {
        if (!enabled) return null
        return withContext(Dispatchers.IO) {
            try { request(bitmap, ocrText) } catch (_: Exception) { null }
        }
    }

    private fun request(bitmap: Bitmap, ocrText: String?): AiProduct? {
        val longest = max(bitmap.width, bitmap.height)
        val small = if (longest > 768) {
            val s = 768f / longest
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * s).toInt(), (bitmap.height * s).toInt(), true)
        } else bitmap
        val jpeg = ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
        val ocr = ocrText.orEmpty().take(9000)
        val prompt = """You identify a product from a shopping screenshot for a personal expense app.
Inspect the image and OCR text together. The screenshot may show a product detail page, a listing card,
several recommendations, or a product beside a large product photo.

Choose the one main product the screenshot is centered on. Prefer its complete visible product title over
brand headers, breadcrumbs, navigation labels, advertisements, feature text inside product images, or suggested
products. Use OCR text to recover spelling and model details, but include only words that clearly belong to the
main product. Do not invent specifications, brand, or identity. If the product cannot be identified, return null
for name and brand.

Do not read or return a price. Prices, MRPs, discounts, EMI values, shipping fees, ratings, and promotional
amounts are handled separately by the on-device scanner and must not affect the product name.

Return JSON only with exactly these fields:
{"name": string or null, "brand": string or null, "category": string, "subcategory": string}
Category and subcategory MUST exactly match one pair in the taxonomy below. If no pair fits, use
"Miscellaneous" and "Uncategorized".

OCR text (may contain unrelated page text):
${ocr.ifBlank { "(none available)" }}

Taxonomy:
$taxonomy""".trimIndent()
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
                .put(JSONObject().put("text", prompt))
                .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg")
                    .put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP)))))))
            .put("generationConfig", JSONObject().put("temperature", 0).put("responseMimeType", "application/json"))
        val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 20000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) return null
            val text = JSONObject(conn.inputStream.bufferedReader().readText())
                .getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
                .getJSONArray("parts").getJSONObject(0).getString("text")
            val o = JSONObject(text)
            fun s(k: String) = if (o.isNull(k)) null else o.optString(k).trim().takeIf { it.isNotEmpty() && !it.equals("null", true) }
            return AiProduct(s("name"), s("brand"), s("category"), s("subcategory"))
        } finally {
            conn.disconnect()
        }
    }
}
