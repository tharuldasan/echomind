package com.echomind.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import android.view.animation.ScaleAnimation
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private lateinit var layoutSuccessCard: LinearLayout
    private lateinit var tvResultTitle: TextView
    private lateinit var tvResultTime: TextView

    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
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
        layoutSuccessCard = findViewById(R.id.layoutSuccessCard)
        tvResultTitle = findViewById(R.id.tvResultTitle)
        tvResultTime = findViewById(R.id.tvResultTime)
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
            // Consume click so inside card won't dismiss
        }

        btnOverlayMic.setOnClickListener {
            if (!isListening) {
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
            // Explicit Sinhala (Sri Lanka) primary language
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "si-LK")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "si-LK")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("si-LK", "en-US", "ta-LK", "si"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                tvOverlayStatus.text = "සවන් දෙමින් පවතී… (මතක් කිරීම පවසන්න)"
                tvOverlaySubtitle.text = "Listening in Sinhala / English"
                startPulseAnimation()
            }

            override fun onBeginningOfSpeech() {
                tvSpokenLive.visibility = View.VISIBLE
                tvSpokenLive.text = "🎙️ ..."
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Animate mic pulse according to voice amplitude
                val scale = 1.0f + (rmsdB.coerceAtLeast(0f) / 10f) * 0.4f
                viewPulseCircle.scaleX = scale
                viewPulseCircle.scaleY = scale
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
                tvOverlayStatus.text = "AI මඟින් වේලාව සහ මතක් කිරීම සකසමින් පවතී…"
                tvOverlaySubtitle.text = "Analyzing reminder with AI..."
                stopPulseAnimation()
            }

            override fun onError(error: Int) {
                isListening = false
                stopPulseAnimation()
                Log.w(TAG, "SpeechRecognizer error: $error")
                
                // If no speech heard, allow tap to retry
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    tvOverlayStatus.text = "කිසිවක් ඇසුණේ නැත. නැවත කතා කරන්න."
                    tvOverlaySubtitle.text = "Tap mic to try again"
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
                    handleSpokenText(spokenText)
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
                handleSpokenText(spokenText)
            } else {
                finish()
            }
        } else {
            finish()
        }
    }

    private fun handleSpokenText(spokenText: String) {
        tvSpokenLive.visibility = View.VISIBLE
        tvSpokenLive.text = "\"$spokenText\""
        tvOverlayStatus.text = "AI මඟින් වේලාව සහ මතක් කිරීම සකසමින් පවතී…"
        tvOverlaySubtitle.text = "Extracting task & schedule with OpenRouter AI..."

        OpenRouterEngine.processVoicePrompt(
            context = this,
            rawText = spokenText,
            onSuccess = { reminder ->
                runOnUiThread {
                    showSuccessAndDismiss(reminder)
                }
            },
            onError = { errorMessage ->
                runOnUiThread {
                    tvOverlayStatus.text = "⚠️ $errorMessage"
                    tvOverlaySubtitle.text = "Tap mic to retry"
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun showSuccessAndDismiss(reminder: Reminder) {
        triggerSuccessFeedback()

        tvOverlayStatus.text = "✅ මතක් කිරීම සාර්ථකව සටහන් විය!"
        tvOverlaySubtitle.text = "Reminder successfully saved!"

        tvResultTitle.text = reminder.title
        if (reminder.targetTimeMs > 0) {
            val sdf = SimpleDateFormat("EEE, MMM d 'at' hh:mm a", Locale.getDefault())
            tvResultTime.text = "⏰ Alarm Set for " + sdf.format(Date(reminder.targetTimeMs))
            tvResultTime.visibility = View.VISIBLE
        } else {
            tvResultTime.text = "📝 Saved as task note"
            tvResultTime.visibility = View.VISIBLE
        }

        layoutSuccessCard.visibility = View.VISIBLE
        tvSpokenLive.visibility = View.GONE

        // Auto-finish after 1.8 seconds so user sees confirmation cleanly
        handler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                finish()
            }
        }, 1800)
    }

    private fun triggerSuccessFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(100)
            }
        } catch (ignored: Exception) {}
    }

    private fun startPulseAnimation() {
        val anim = AlphaAnimation(0.25f, 0.7f).apply {
            duration = 600
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
        handler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.destroy()
        } catch (ignored: Exception) {}
    }
}
