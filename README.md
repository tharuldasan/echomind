# 🎙️ EchoMind AI (VocalCue AI)
> Instant Multilingual Voice-to-Reminder Android App powered by OpenRouter AI & Background Hardware Triggers.

![EchoMind AI](app/src/main/res/drawable/app_logo.png)

---

## 🌟 Key Features

1. **Physical Hardware Key Trigger (Accessibility Service)**:
   - Double-click the **Volume Down** button anywhere in the Android system (even when screen is locked or app is closed) to immediately launch voice reminder capture.
2. **Multilingual Speech Recognition & AI Reasoning**:
   - Supports spoken audio in **English, Sinhala, Tamil, Hindi, and colloquial mixed speech** (Singlish/Tanglish).
   - Powered by OpenRouter AI (Free models: `meta-llama/llama-3.3-70b-instruct:free`, `google/gemini-2.0-flash-lite:free`, etc.) to parse complex relative times (*"heta ude 8ta"*, *"in 45 minutes"*, *"call mom tomorrow evening"*) into structured tasks and exact timestamps.
3. **Exact Alarms & Doze Mode Bypass**:
   - Uses Android `AlarmManager.setExactAndAllowWhileIdle()` to ensure alarms ring on time regardless of Android Doze Mode or task-clearing.
   - High-priority heads-up notifications with sound and vibration.
4. **Device Compatibility (Samsung Galaxy M02, Vivo Y93 & Android 8.0 - 14+)**:
   - `minSdk = 26` (Android 8.0 Oreo), specifically tested and tailored for budget phones like **Vivo Y93** (FuntouchOS) and **Samsung Galaxy M02** (OneUI Core).
   - Built-in Power Management helpers to bypass aggressive OEM background task killing.
5. **Reboot Persistence**:
   - `BootReceiver` automatically restores all future alarms if the phone reboots.
6. **Automated GitHub Compilation**:
   - Pre-configured GitHub Actions CI workflow in `.github/workflows/build.yml` that automatically compiles the APK whenever you push to GitHub!

---

## 🚀 How to Compile with GitHub & Download APK

You don't need Android Studio on your PC. You can compile this app directly on GitHub:

1. **Push this project to a GitHub repository**:
   ```bash
   git init
   git add .
   git commit -m "Initial commit of EchoMind AI"
   git branch -M main
   git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO_NAME.git
   git push -u origin main
   ```

2. **Wait for GitHub Actions to build**:
   - Go to your repository on GitHub.
   - Click the **"Actions"** tab.
   - Click on the active workflow **"Build EchoMind AI APK"**.

3. **Download the APK**:
   - When the workflow finishes (green checkmark), scroll down to the **Artifacts** section.
   - Download **`EchoMind-AI-Debug-APK`**, unzip it, and install `app-debug.apk` on your phone!

---

## ⚙️ First-Time Setup on Phone

### 1. Enable Hardware Trigger (Volume Down Double-Click)
1. Open the **EchoMind AI** app.
2. Tap **"Enable Accessibility Service"** (or go to *Settings > Accessibility > Installed Apps / Downloaded Services*).
3. Turn on **"EchoMind Hardware Trigger"**.
4. Now, double-click **Volume Down** anywhere to trigger voice capture!

### 2. Battery Optimization (Crucial for Vivo & Samsung)
- **Vivo Y93 / Funtouch OS**: Open *iManager > App Manager > Autostart*, and enable **EchoMind AI**. Tap *High Background Power Consumption* and toggle **Allow**.
- **Samsung Galaxy M02 / OneUI**: Go to *Settings > Apps > EchoMind AI > Battery > Set to "Unrestricted"*.

### 3. OpenRouter API Key
- The app comes pre-configured with your OpenRouter API key.
- To change the API key or switch AI models (e.g. to Gemini Flash or Llama 3.3), tap the **Settings ⚙️** icon in the top right.

---

## 📂 Project Architecture

```
app/
├── .github/
│   └── workflows/
│       └── build.yml                   # Automated GitHub Actions CI workflow
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties   # Gradle 8.4 wrapper config
├── app/
│   ├── build.gradle.kts                # App module build script (SDK 26 - 34)
│   ├── proguard-rules.pro              # Proguard configuration
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml     # Manifest with full permissions & services
│           ├── java/com/echomind/ai/
│           │   ├── MainActivity.kt     # Main UI, voice recognition flow, permission handling
│           │   ├── KeyListenService.kt # AccessibilityService for hardware volume button double-click
│           │   ├── OpenRouterEngine.kt # OpenRouter AI caller & JSON parser
│           │   ├── AlarmReceiver.kt    # Alarm notification & vibration receiver
│           │   ├── BootReceiver.kt     # Re-schedules alarms on phone restart
│           │   ├── PowerManagementHelper.kt # Samsung/Vivo power manager helper
│           │   ├── ReminderManager.kt  # Local storage & alarm scheduler
│           │   ├── Reminder.kt         # Data model
│           │   └── RemindersAdapter.kt # RecyclerView adapter for cards
│           └── res/
│               ├── drawable/           # App logo, vector icons, shapes
│               ├── layout/             # activity_main, item_reminder, dialog_settings
│               ├── mipmap-.../         # Launcher icons generated from logo.jpg
│               ├── values/             # colors, strings, themes
│               └── xml/                # accessibility_service_config.xml
├── build.gradle.kts                    # Root build script
├── settings.gradle.kts                 # Settings script
├── gradle.properties                   # Gradle properties
├── gradlew                             # Gradle wrapper shell script
└── gradlew.bat                         # Gradle wrapper Windows batch script
```
