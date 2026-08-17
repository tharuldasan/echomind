package com.echomind.ai

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_START_RECORDING = "ACTION_START_RECORDING"
    }

    private lateinit var statusText: TextView
    private lateinit var btnRecord: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var rvReminders: RecyclerView
    private lateinit var tvEmptyReminders: TextView
    private lateinit var tvReminderCount: TextView

    // Status Cards
    private lateinit var cardAccessibility: MaterialCardView
    private lateinit var tvAccessBadge: TextView
    private lateinit var btnEnableAccessibility: MaterialButton
    private lateinit var cardBattery: MaterialCardView
    private lateinit var tvBatteryBadge: TextView
    private lateinit var btnDisableBatteryOpt: MaterialButton

    private lateinit var adapter: RemindersAdapter

    // Permission Launcher for Record Audio & Notifications
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (recordGranted) {
            launchVoiceOverlay()
        } else {
            Toast.makeText(this, "Microphone permission is required for voice capture.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupRecyclerView()
        checkSystemRequirements()

        // Start background keep-alive service
        try {
            EchoMindService.start(this)
        } catch (ignored: Exception) {}

        btnRecord.setOnClickListener {
            handleRecordClick()
        }

        btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        btnEnableAccessibility.setOnClickListener {
            PowerManagementHelper.openAccessibilitySettings(this)
        }

        btnDisableBatteryOpt.setOnClickListener {
            PowerManagementHelper.requestIgnoreBatteryOptimization(this)
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        checkSystemRequirements()
        refreshRemindersList()
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        btnRecord = findViewById(R.id.btnRecord)
        btnSettings = findViewById(R.id.btnSettings)
        rvReminders = findViewById(R.id.rvReminders)
        tvEmptyReminders = findViewById(R.id.tvEmptyReminders)
        tvReminderCount = findViewById(R.id.tvReminderCount)

        cardAccessibility = findViewById(R.id.cardAccessibility)
        tvAccessBadge = findViewById(R.id.tvAccessBadge)
        btnEnableAccessibility = findViewById(R.id.btnEnableAccessibility)

        cardBattery = findViewById(R.id.cardBattery)
        tvBatteryBadge = findViewById(R.id.tvBatteryBadge)
        btnDisableBatteryOpt = findViewById(R.id.btnDisableBatteryOpt)
    }

    private fun setupRecyclerView() {
        adapter = RemindersAdapter(emptyList()) { reminder ->
            ReminderManager.deleteReminder(this, reminder.id)
            refreshRemindersList()
            Toast.makeText(this, "Reminder removed", Toast.LENGTH_SHORT).show()
        }
        rvReminders.layoutManager = LinearLayoutManager(this)
        rvReminders.adapter = adapter
        refreshRemindersList()
    }

    private fun refreshRemindersList() {
        val list = ReminderManager.getAllReminders(this)
        adapter.updateData(list)
        tvReminderCount.text = "${list.size} Reminders"
        tvEmptyReminders.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun checkSystemRequirements() {
        // 1. Accessibility Service Check
        val isAccessEnabled = PowerManagementHelper.isAccessibilityServiceEnabled(this, KeyListenService::class.java)
        if (isAccessEnabled) {
            tvAccessBadge.text = "Active"
            tvAccessBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            btnEnableAccessibility.visibility = View.GONE
        } else {
            tvAccessBadge.text = "Disabled"
            tvAccessBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_amber))
            btnEnableAccessibility.visibility = View.VISIBLE
        }

        // 2. Battery Optimization Check (Galaxy M02 / Vivo Y93 / general Android)
        val isBatteryIgnored = PowerManagementHelper.isIgnoringBatteryOptimizations(this)
        if (isBatteryIgnored) {
            tvBatteryBadge.text = "Optimized"
            tvBatteryBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            btnDisableBatteryOpt.visibility = View.GONE
        } else {
            tvBatteryBadge.text = "Restricted"
            tvBatteryBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_amber))
            btnDisableBatteryOpt.visibility = View.VISIBLE
        }

        // 3. Exact Alarm Permission check for Android 12+ (SDK 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (ignored: Exception) {}
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START_RECORDING, false) == true) {
            handleRecordClick()
        }
    }

    private fun handleRecordClick() {
        val neededPermissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (neededPermissions.isNotEmpty()) {
            permissionLauncher.launch(neededPermissions.toTypedArray())
        } else {
            launchVoiceOverlay()
        }
    }

    private fun launchVoiceOverlay() {
        val intent = Intent(this, VoiceOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val etApiKey = dialogView.findViewById<TextInputEditText>(R.id.etApiKey)
        val etModel = dialogView.findViewById<TextInputEditText>(R.id.etModel)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnSaveSettings)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancelSettings)

        etApiKey.setText(OpenRouterEngine.getApiKey(this))
        etModel.setText(OpenRouterEngine.getModel(this))

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val key = etApiKey.text?.toString()?.trim() ?: ""
            val model = etModel.text?.toString()?.trim() ?: ""

            if (key.isNotEmpty()) {
                OpenRouterEngine.setApiKey(this, key)
            }
            if (model.isNotEmpty()) {
                OpenRouterEngine.setModel(this, model)
            }

            Toast.makeText(this, "Settings updated successfully!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }
}
