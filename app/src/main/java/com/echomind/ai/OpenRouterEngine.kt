package com.echomind.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
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

    private const val TAG = "AIEngine"
    private const val PREFS_SETTINGS = "echomind_settings_prefs"
    private const val KEY_CUSTOM_API_KEY = "custom_api_key"
    private const val KEY_CUSTOM_MODEL = "custom_model"
    private const val CONFIRMATION_CHANNEL_ID = "echomind_confirmation_channel"

    // Default configuration (Gemini Flash on OpenRouter / Free Models)
    const val DEFAULT_API_KEY = "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"
    const val DEFAULT_MODEL = "google/gemini-2.0-flash-001"
    const val FALLBACK_MODEL = "nvidia/nemotron-3.5-lightning:free"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
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

    /**
     * Executes AI prompt parsing in background, schedules alarm, and notifies user with confirmation.
     */
    fun processVoicePromptInBackground(
        context: Context,
        rawText: String
    ) {
        processVoicePrompt(
            context = context,
            rawText = rawText,
            onSuccess = { reminder ->
                showAsyncConfirmation(context, reminder)
            },
            onError = { errorMessage ->
                Log.e(TAG, "Background AI processing failed: $errorMessage")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "⚠️ මතක් කිරීම සටහන් කිරීමට අපොහොසත් විය: $errorMessage", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    fun processVoicePrompt(
        context: Context,
        rawText: String,
        onSuccess: (reminder: Reminder) -> Unit,
        onError: (errorMessage: String) -> Unit
    ) {
        val apiKey = getApiKey(context).trim()
        val model = getModel(context).trim()

        val now = Date()
        val currentFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm (EEEE)", Locale.ENGLISH).format(now)

        val systemPrompt = """
            You are an expert multilingual AI voice reminder engine with native understanding of Sinhala (සිංහල), English, Tamil, and Singlish/Tanglish.
            Current reference time: $currentFormatted. Timezone: ${TimeZone.getDefault().id}.

            Sinhala temporal words:
            - "හෙට" / "heta" = tomorrow
            - "අද" / "ada" = today
            - "උදේ" / "ude" = morning (AM)
            - "හවස" / "hawasa" / "දවල්" / "dawal" / "රෑ" / "ra" = afternoon / evening / night (PM)
            - "විනාඩි" / "winadi" = minutes (e.g. "තව විනාඩි 10කින්" / "thawa winadi 10kin" = in 10 minutes from now)
            - "පැය" / "paya" = hours (e.g. "පැය 2කින්" = in 2 hours from now)
            - "බෙහෙත් බොන්න" = Take medicine
            - "කතා කරන්න" / "call ekak denna" = Call

            Rules:
            1. Parse user's spoken voice into a clean, concise task description (clean_idea).
            2. Calculate exact future alarm timestamp in 'yyyy-MM-dd HH:mm' format based on the current reference time.
            3. If no specific time was spoken, set target_time_formatted to "".

            Return RAW JSON ONLY matching this format:
            {
              "clean_idea": "Clean synthesized task title in English",
              "target_time_formatted": "yyyy-MM-dd HH:mm"
            }
        """.trimIndent()

        // Check if user provided a Direct Google Gemini API Key (starts with AIza)
        if (apiKey.startsWith("AIza")) {
            callGoogleGeminiDirect(context, apiKey, systemPrompt, rawText, onSuccess, onError)
        } else {
            callOpenRouter(context, apiKey, model, systemPrompt, rawText, onSuccess, onError)
        }
    }

    private fun callGoogleGeminiDirect(
        context: Context,
        apiKey: String,
        systemPrompt: String,
        rawText: String,
        onSuccess: (reminder: Reminder) -> Unit,
        onError: (errorMessage: String) -> Unit
    ) {
        val fullPrompt = "$systemPrompt\n\nUser Spoken Voice: \"$rawText\""

        val jsonPayload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", fullPrompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("response_mime_type", "application/json")
                put("temperature", 0.1)
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Gemini direct call failed", e)
                onError("Network error: ${e.localizedMessage}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseStr = response.body?.string() ?: ""
                Log.d(TAG, "Gemini direct response: $responseStr")

                if (!response.isSuccessful) {
                    onError("Gemini API Error (${response.code}): $responseStr")
                    return
                }

                try {
                    val jsonObj = JSONObject(responseStr)
                    val candidates = jsonObj.getJSONArray("candidates")
                    val contentParts = candidates.getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                    val textOutput = contentParts.getJSONObject(0).getString("text")

                    parseAndSaveReminder(context, textOutput, rawText, onSuccess)
                } catch (e: Exception) {
                    Log.e(TAG, "Gemini parse error", e)
                    onError("Failed to parse Gemini response.")
                }
            }
        })
    }

    private fun callOpenRouter(
        context: Context,
        apiKey: String,
        model: String,
        systemPrompt: String,
        rawText: String,
        onSuccess: (reminder: Reminder) -> Unit,
        onError: (errorMessage: String) -> Unit
    ) {
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
                    // Try fallback model if first model failed
                    if (model != FALLBACK_MODEL) {
                        Log.w(TAG, "Model $model failed, trying fallback $FALLBACK_MODEL")
                        callOpenRouter(context, apiKey, FALLBACK_MODEL, systemPrompt, rawText, onSuccess, onError)
                        return
                    }
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

                    parseAndSaveReminder(context, contentRaw, rawText, onSuccess)

                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing AI response: $responseStr", e)
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

    private fun parseAndSaveReminder(
        context: Context,
        contentRaw: String,
        rawText: String,
        onSuccess: (reminder: Reminder) -> Unit
    ) {
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

        val reminder = Reminder(
            id = UUID.randomUUID().toString(),
            title = if (cleanIdea.isNotBlank()) cleanIdea else rawText,
            targetTimeMs = timeMs,
            originalText = rawText,
            isTriggered = false,
            createdAt = System.currentTimeMillis()
        )

        ReminderManager.saveReminder(context, reminder)
        onSuccess(reminder)
    }

    private fun extractJsonFromResponse(raw: String): JSONObject {
        val trimmed = raw.trim()
        val pattern = Pattern.compile("\\{.*\\}", Pattern.DOTALL)
        val matcher = pattern.matcher(trimmed)
        if (matcher.find()) {
            val jsonSnippet = matcher.group()
            return JSONObject(jsonSnippet)
        }
        return JSONObject(trimmed)
    }

    private fun showAsyncConfirmation(context: Context, reminder: Reminder) {
        val timeDesc = if (reminder.targetTimeMs > 0) {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            "වේලාව: " + sdf.format(Date(reminder.targetTimeMs))
        } else {
            "සටහන් කරගන්නා ලදී"
        }

        val toastMsg = "⏰ මතක් කිරීම සටහන් විය: ${reminder.title} ($timeDesc)"

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, toastMsg, Toast.LENGTH_LONG).show()
        }

        // Trigger brief haptic pulse
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(120)
            }
        } catch (ignored: Exception) {}

        // Also post a high-visibility confirmation notification
        postConfirmationNotification(context, reminder, timeDesc)
    }

    private fun postConfirmationNotification(context: Context, reminder: Reminder, timeDesc: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CONFIRMATION_CHANNEL_ID,
                "EchoMind Reminder Confirmations",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows confirmation when a new voice reminder is scheduled"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            reminder.id.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CONFIRMATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle("✅ මතක් කිරීම සටහන් විය (Reminder Set)")
            .setContentText("${reminder.title} • $timeDesc")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(reminder.id.hashCode(), notification)
    }
}
