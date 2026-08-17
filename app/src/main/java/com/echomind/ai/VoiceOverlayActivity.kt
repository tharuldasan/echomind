package com.echomind.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView

class VoiceOverlayActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VoiceOverlayActivity"
    }

    private lateinit var rootOverlay: RelativeLayout
    private lateinit var cardFloating: MaterialCardView
    private lateinit var btnCloseOverlay: ImageButton
    private lateinit var btnOverlayMic: ImageButton
    private lateinit var viewPulseCircle: View
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvOverlaySubtitle: TextView
    private lateinit var tvSpokenLive: TextView

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startInAppSpeechRecognition()
        } else {
            Toast.makeText(this, "Microphone permission is required.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_overlay)

        initViews()
        setupListeners()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startInAppSpeechRecognition()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun initViews() {
        rootOverlay = findViewById(R.id.rootOverlay)
        cardFloating = findViewById(R.id.cardFloating)
        btnCloseOverlay = findViewById(R.id.btnCloseOverlay)
        btnOverlayMic = findViewById(R.id.btnOverlayMic)
        viewPulseCircle = findViewById(R.id.viewPulseCircle)
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvOverlaySubtitle = findViewById(R.id.tvOverlaySubtitle)
        tvSpokenLive = findViewById(R.id.tvSpokenLive)
    }

    private fun setupListeners() {
        btnCloseOverlay.setOnClickListener {
            stopListeningAndFinish()
        }

        // Tap outside card to dismiss
        rootOverlay.setOnClickListener {
            stopListeningAndFinish()
        }

        cardFloating.setOnClickListener {
            // Consume click
        }

        // Tap mic while listening to immediately stop and process
        btnOverlayMic.setOnClickListener {
            if (isListening) {
                try {
                    speechRecognizer?.stopListening()
                } catch (ignored: Exception) {}
            } else {
                startInAppSpeechRecognition()
            }
        }
    }

    private fun startInAppSpeechRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            fallbackToIntentSpeech()
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Primary Sinhala (Sri Lanka)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "si-LK")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "si-LK")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("si-LK", "en-US", "ta-LK", "si"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 2)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)

            // Fast silence detection (No waiting lag)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 600L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 600L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 600L)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                tvOverlayStatus.text = "සවන් දෙමින් පවතී… (මතක් කිරීම පවසන්න)"
                tvOverlaySubtitle.text = "Listening (Tap mic when done)"
                startPulseAnimation()
            }

            override fun onBeginningOfSpeech() {
                tvSpokenLive.visibility = View.VISIBLE
                tvSpokenLive.text = "🎙️ ..."
            }

            override fun onRmsChanged(rmsdB: Float) {
                val scale = 1.0f + (rmsdB.coerceAtLeast(0f) / 10f) * 0.4f
                viewPulseCircle.scaleX = scale
                viewPulseCircle.scaleY = scale
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
                stopPulseAnimation()
            }

            override fun onError(error: Int) {
                isListening = false
                stopPulseAnimation()
                Log.w(TAG, "SpeechRecognizer error: $error")
                
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    tvOverlayStatus.text = "කිසිවක් ඇසුණේ නැත. නැවත කතා කරන්න."
                    tvOverlaySubtitle.text = "Tap mic to retry"
                } else {
                    fallbackToIntentSpeech()
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                stopPulseAnimation()
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.firstOrNull() ?: ""

                if (spokenText.isNotBlank()) {
                    dispatchAndCloseImmediately(spokenText)
                } else {
                    tvOverlayStatus.text = "කිසිවක් ඇසුණේ නැත."
                    tvOverlaySubtitle.text = "Tap mic to retry"
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val liveText = partials?.firstOrNull()
                if (!liveText.isNullOrBlank()) {
                    tvSpokenLive.visibility = View.VISIBLE
                    tvSpokenLive.text = liveText
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun fallbackToIntentSpeech() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "si-LK")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "si-LK")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "මතක් කිරීම පවසන්න (Sinhala / English)...")
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, 201)
        } catch (e: Exception) {
            Toast.makeText(this, "Speech recognition unavailable", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 201 && resultCode == RESULT_OK && data != null) {
            val matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull() ?: ""
            if (spokenText.isNotBlank()) {
                dispatchAndCloseImmediately(spokenText)
            } else {
                finish()
            }
        } else {
            finish()
        }
    }

    /**
     * Instantly triggers background AI processing and closes the overlay without waiting!
     */
    private fun dispatchAndCloseImmediately(spokenText: String) {
        // Quick haptic feedback
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        } catch (ignored: Exception) {}

        Toast.makeText(applicationContext, "🎙️ \"$spokenText\"\nමතක් කිරීම සකසමින් පවතී...", Toast.LENGTH_SHORT).show()

        // Run AI processing in background
        OpenRouterEngine.processVoicePromptInBackground(applicationContext, spokenText)

        // Close overlay immediately so user is not blocked!
        finish()
    }

    private fun startPulseAnimation() {
        val anim = AlphaAnimation(0.25f, 0.7f).apply {
            duration = 500
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        viewPulseCircle.startAnimation(anim)
    }

    private fun stopPulseAnimation() {
        viewPulseCircle.clearAnimation()
        viewPulseCircle.scaleX = 1.0f
        viewPulseCircle.scaleY = 1.0f
    }

    private fun stopListeningAndFinish() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (ignored: Exception) {}
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            speechRecognizer?.destroy()
        } catch (ignored: Exception) {}
    }
}
