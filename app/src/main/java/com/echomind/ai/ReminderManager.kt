package com.echomind.ai

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.json.JSONArray
import java.util.UUID

object ReminderManager {
    private const val PREFS_NAME = "echomind_reminders_prefs"
    private const val KEY_REMINDERS = "saved_reminders"
    private const val TAG = "ReminderManager"

    fun getAllReminders(context: Context): List<Reminder> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_REMINDERS, null) ?: return emptyList()
        val list = mutableListOf<Reminder>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                list.add(Reminder.fromJsonObject(array.getJSONObject(i)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing saved reminders", e)
        }
        return list.sortedByDescending { it.createdAt }
    }

    fun saveReminder(context: Context, reminder: Reminder) {
        val current = getAllReminders(context).toMutableList()
        current.removeAll { it.id == reminder.id }
        current.add(0, reminder)
        persistList(context, current)

        if (reminder.targetTimeMs > System.currentTimeMillis()) {
            scheduleAlarm(context, reminder)
        }
    }

    fun deleteReminder(context: Context, reminderId: String) {
        val current = getAllReminders(context).toMutableList()
        val target = current.find { it.id == reminderId }
        if (target != null) {
            cancelAlarm(context, target)
            current.remove(target)
            persistList(context, current)
        }
    }

    fun markReminderTriggered(context: Context, reminderId: String) {
        val current = getAllReminders(context).toMutableList()
        val index = current.indexOfFirst { it.id == reminderId }
        if (index != -1) {
            val item = current[index]
            current[index] = item.copy(isTriggered = true)
            persistList(context, current)
        }
    }

    fun rescheduleAllPendingAlarms(context: Context) {
        val reminders = getAllReminders(context)
        val now = System.currentTimeMillis()
        for (reminder in reminders) {
            if (!reminder.isTriggered && reminder.targetTimeMs > now) {
                scheduleAlarm(context, reminder)
            }
        }
    }

    fun scheduleAlarm(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_REMINDER_ID, reminder.id)
            putExtra(AlarmReceiver.EXTRA_TASK_TITLE, reminder.title)
            putExtra(AlarmReceiver.EXTRA_ORIGINAL_VOICE, reminder.originalText)
            putExtra(AlarmReceiver.EXTRA_TARGET_TIME, reminder.targetTimeMs)
        }

        val requestCode = reminder.id.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.targetTimeMs,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    reminder.targetTimeMs,
                    pendingIntent
                )
            }
            Log.d(TAG, "Alarm scheduled for '${reminder.title}' at ${reminder.targetTimeMs}")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling exact alarm", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling alarm", e)
        }
    }

    fun cancelAlarm(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java)
        val requestCode = reminder.id.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun persistList(context: Context, list: List<Reminder>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        for (item in list) {
            array.put(item.toJsonObject())
        }
        prefs.edit().putString(KEY_REMINDERS, array.toString()).apply()
    }
}
