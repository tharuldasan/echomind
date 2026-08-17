package com.echomind.ai

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class KeyListenService : AccessibilityService() {

    companion object {
        private const val TAG = "KeyListenService"
        private const val DOUBLE_CLICK_TIME_DELTA = 550L
    }

    private var lastVolumeUpTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Required callback for AccessibilityService
    }

    override fun onInterrupt() {
        Log.w(TAG, "KeyListenService interrupted")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Sticky background service mode
        return START_STICKY
    }

    @SuppressLint("InvalidWakeLockTag")
    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Detect double click on Volume UP button anywhere in Android
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP && event.action == KeyEvent.ACTION_DOWN) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastVolumeUpTime < DOUBLE_CLICK_TIME_DELTA) {
                lastVolumeUpTime = 0L
                Log.d(TAG, "Double-click Volume UP detected! Launching mini voice overlay...")

                // Acquire temporary screen wake lock to ensure phone is awake
                try {
                    val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                    @Suppress("DEPRECATION")
                    val wakeLock = powerManager?.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "EchoMind:WakeLock"
                    )
                    wakeLock?.acquire(3000)
                } catch (e: Exception) {
                    Log.e(TAG, "WakeLock error", e)
                }

                // Launch compact floating mini voice overlay activity directly over current screen
                val overlayIntent = Intent(this, VoiceOverlayActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(overlayIntent)
                return true
            }
            lastVolumeUpTime = currentTime
        }
        return super.onKeyEvent(event)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task swiped from recents - maintaining background trigger")

        // Ensure service restarts even if aggressive task killer swipes from recents
        val restartIntent = Intent(applicationContext, KeyListenService::class.java).apply {
            setPackage(packageName)
        }
        val restartPending = PendingIntent.getService(
            applicationContext,
            101,
            restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        alarmManager?.set(
            AlarmManager.RTC,
            System.currentTimeMillis() + 1000,
            restartPending
        )
    }
}
