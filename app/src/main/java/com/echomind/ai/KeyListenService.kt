package com.echomind.ai

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
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

    private var lastVolumeDownTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for key filtering, but required by AccessibilityService
    }

    override fun onInterrupt() {
        Log.w(TAG, "KeyListenService interrupted")
    }

    @SuppressLint("InvalidWakeLockTag")
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastVolumeDownTime < DOUBLE_CLICK_TIME_DELTA) {
                lastVolumeDownTime = 0L
                Log.d(TAG, "Double-click Volume Down detected! Triggering voice capture...")

                // Acquire temporary wake lock to ensure phone is awake
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

                // Launch MainActivity to start instant listening
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(MainActivity.EXTRA_START_RECORDING, true)
                }
                startActivity(intent)
                return true
            }
            lastVolumeDownTime = currentTime
        }
        return super.onKeyEvent(event)
    }
}
