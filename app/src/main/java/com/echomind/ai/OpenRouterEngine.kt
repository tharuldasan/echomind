package com.echomind.ai

import android.content.Context
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object OpenRouterEngine {

    private const val TAG = "OpenRouterEngine"
    private const val PREFS_SETTINGS = "echomind_settings_prefs"
    private const val KEY_CUSTOM_API_KEY = "custom_api_key"
    private const val KEY_CUSTOM_MODEL = "custom_model"

    // Default OpenRouter API configuration
    const val DEFAULT_API_KEY = "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"
    const val DEFAULT_MODEL = "meta-llama/llama-3.3-70b-instruct:free"

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    fun getApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_CUSTOM_API_KEY, "")
        return if (!saved.isNullOrBlank()) saved else DEFAULT_API_KEY
    }

    fun setApiKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_API_KEY, key.trim()).apply()
    }

    fun getModel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_CUSTOM_MODEL, "")
        return if (!saved.isNullOrBlank()) saved else DEFAULT_MODEL
    }

    fun setModel(context: Context, model: String) {
        val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_MODEL, model.trim()).apply()
    }

    fun processVoicePrompt(
        context: Context,
        rawText: String,
        onSuccess: (reminder: Reminder) -> Unit,
        onError: (errorMessage: String) -> Unit
    ) {
        val apiKey = getApiKey(context)
        val model = getModel(context)

        val now = Date()
        val currentFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm (EEEE)", Locale.ENGLISH).format(now)

        val systemPrompt = """
            You are a multilingual AI voice reminder engine.
            The user speaks in any language (Sinhala, English, Tamil, Hindi, etc.) or Singlish/Tanglish.
            Current reference time: $currentFormatted. Timezone: ${TimeZone.getDefault().id}.

            Your goal is to parse user intent into a clean task title in English and an exact scheduled alarm time in 'yyyy-MM-dd HH:mm' format.
            Rules:
            1. If the user mentions relative time (e.g. "in 15 minutes", "after 2 hours", "heta ude 7ta", "tomorrow morning at 9am"), calculate the exact target time relative to current time.
            2. If no time is mentioned, return empty string "" for target_time_formatted.
            3. "clean_idea" must be a concise, clean action/task description translated to English.

            You MUST respond with RAW JSON ONLY (no markdown code blocks, no explanations).
            JSON schema:
            {
              "clean_idea": "Clean task title in English",
              "target_time_formatted": "yyyy-MM-dd HH:mm"
            }
        """.trimIndent()

        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", rawText)
            })
        }

        val jsonPayload = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("temperature", 0.1)
        }

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://echomind.ai")
            .addHeader("X-Title", "EchoMind AI")
            .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "OpenRouter call failed", e)
                onError("Network error: ${e.localizedMessage ?: "Could not connect"}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseStr = response.body?.string() ?: ""
                Log.d(TAG, "OpenRouter response: $responseStr")

                if (!response.isSuccessful) {
                    onError("AI Service Error (${response.code}): $responseStr")
                    return
                }

                try {
                    val jsonObj = JSONObject(responseStr)
                    val choices = jsonObj.optJSONArray("choices")
                    if (choices == null || choices.length() == 0) {
                        onError("No response returned by AI model.")
                        return
                    }

                    val contentRaw = choices.getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")

                    val parsedResult = extractJsonFromResponse(contentRaw)
                    val cleanIdea = parsedResult.optString("clean_idea", rawText).trim()
                    val timeStr = parsedResult.optString("target_time_formatted", "").trim()

                    var timeMs = 0L
                    if (timeStr.isNotEmpty()) {
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH)
                        sdf.timeZone = TimeZone.getDefault()
                        val parsedDate = sdf.parse(timeStr)
                        if (parsedDate != null) {
                            timeMs = parsedDate.time
                        }
                    }

                    // Fallback if AI gave a time in the past or within 10 seconds, adjust if needed
                    val reminder = Reminder(
                        id = UUID.randomUUID().toString(),
                        title = if (cleanIdea.isNotBlank()) cleanIdea else rawText,
                        targetTimeMs = timeMs,
                        originalText = rawText,
                        isTriggered = false,
                        createdAt = System.currentTimeMillis()
                    )

                    // Save and schedule via ReminderManager
                    ReminderManager.saveReminder(context, reminder)
                    onSuccess(reminder)

                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing AI response: $responseStr", e)
                    // Graceful fallback: save raw reminder
                    val fallbackReminder = Reminder(
                        id = UUID.randomUUID().toString(),
                        title = rawText,
                        targetTimeMs = 0L,
                        originalText = rawText,
                        isTriggered = false
                    )
                    ReminderManager.saveReminder(context, fallbackReminder)
                    onSuccess(fallbackReminder)
                }
            }
        })
    }

    private fun extractJsonFromResponse(raw: String): JSONObject {
        val trimmed = raw.trim()
        // Check if wrapped in markdown code fence ```json ... ```
        val pattern = Pattern.compile("\\{.*\\}", Pattern.DOTALL)
        val matcher = pattern.matcher(trimmed)
        if (matcher.find()) {
            val jsonSnippet = matcher.group()
            return JSONObject(jsonSnippet)
        }
        return JSONObject(trimmed)
    }
}
