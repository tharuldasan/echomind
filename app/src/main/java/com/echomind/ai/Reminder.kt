package com.echomind.ai

import org.json.JSONObject

data class Reminder(
    val id: String,
    val title: String,
    val targetTimeMs: Long,
    val originalText: String,
    val isTriggered: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("targetTimeMs", targetTimeMs)
            put("originalText", originalText)
            put("isTriggered", isTriggered)
            put("createdAt", createdAt)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): Reminder {
            return Reminder(
                id = json.getString("id"),
                title = json.getString("title"),
                targetTimeMs = json.getLong("targetTimeMs"),
                originalText = json.optString("originalText", ""),
                isTriggered = json.optBoolean("isTriggered", false),
                createdAt = json.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}
