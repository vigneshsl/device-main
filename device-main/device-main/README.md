# Smart Network Alert

**Smart Network Alert** is a security-hardened, event-driven native Android application (`minSdk 31`, `targetSdk 35`, Kotlin + Jetpack Compose + Material 3) that monitors real-time cellular network generation transitions (**5G ↔ 4G ↔ 3G ↔ 2G**) on the default data SIM and plays customizable acoustic and haptic alerts—for example, when dropping from **5G → 4G**.

---

## Core Architecture & Highlights

1. **Accuracy-First Cellular Detection (`NetworkGenerationMapper`)**
   - Uses `TelephonyManager.registerTelephonyCallback` with `TelephonyCallback.DisplayInfoListener`, `TelephonyCallback.SignalStrengthsListener`, and `TelephonyCallback.ServiceStateListener`.
   - Maps to **5G** when `overrideNetworkType` is `OVERRIDE_NETWORK_TYPE_NR_NSA` or `OVERRIDE_NETWORK_TYPE_NR_ADVANCED` (including `NR_NSA_MMWAVE`), or when `networkType` is `NETWORK_TYPE_NR`.
   - Maps `NETWORK_TYPE_LTE` / `LTE_CA` / `LTE_ADVANCED_PRO` to **4G**, UMTS/HSPA/EVDO/EHRPD/TD-SCDMA variants to **3G**, and GPRS/EDGE/GSM/CDMA/1xRTT/IDEN to **2G**.
   - Displays honest **"Unknown"** whenever permission is denied, Airplane Mode is active, SIM is absent, or the radio is out of service. Never fabricates readings.
   - **Dual-SIM Clarity**: Binds to `SubscriptionManager.getDefaultDataSubscriptionId()` and displays the active data subscription in the UI.

2. **Negligible Battery Drain (Zero Polling)**
   - 100% event-driven binder callbacks. Zero polling loops, zero `Timer`s, zero `WakeLock`s, zero `AlarmManager`, and zero periodic `WorkManager` tasks.
   - Runs a single `specialUse` foreground service (`NetworkMonitorService`) **only** while the user toggles Monitoring **ON**. Immediately unregisters `TelephonyCallback` and stops the service when toggled **OFF**.
   - Configurable coroutine debounce (`delay(debounceMs)`, default `3.0 s`) cancels false alerts if the network reverts before the timer expires.
   - Audio playback (`AudioTrack` for synthesized PCM tones or `MediaPlayer` for validated custom files) is instantiated **only** when an alert triggers and released in `finally` immediately after playback completes.
   - Static foreground notification (`setOnlyAlertOnce(true)`, `IMPORTANCE_LOW`) with zero per-second updates.

3. **Security & Privacy Hardening**
   - **Minimal Permissions**: Requests only `READ_PHONE_STATE`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, and `FOREGROUND_SERVICE_SPECIAL_USE`. Does **not** request `INTERNET`, location, contacts, phone numbers, or hardware identifiers.
   - **Manifest Hardening**: `android:allowBackup="false"`, `dataExtractionRules` and `fullBackupContent` excluding all domains, `android:usesCleartextTraffic="false"`, and `android:exported="false"` on all components except `MainActivity`.
   - **Custom Sound Sandbox (`CustomSoundStorage`)**: Validates MIME type (`audio/*`), enforces a strict `≤ 2 MB` size limit during streaming copy, verifies container header magic bytes (`RIFF`, `OggS`, `ID3`, `fLaC`, `ftyp`), and verifies track decodability via `MediaExtractor` before storing in app-private `filesDir/custom_alerts/`. Never persists raw external URIs.

---

## Phone-Only Workflow: Upload to GitHub, Build via CI, Download & Install APK

You can upload this project, compile signed APKs in GitHub Actions, and install the app entirely from an Android phone without needing a desktop PC:

### Step 1: Upload Project from Your Phone to GitHub
1. Export or download the project ZIP from AI Studio to your phone's `Downloads` folder.
2. Open your mobile browser (Chrome / Firefox) and sign in to [github.com](https://github.com).
3. Tap the **`+`** icon in the top-right corner → **New repository**.
4. Name the repository `smart-network-alert`, select **Private** (or Public), and tap **Create repository**.
5. If uploading individual files or pushing via Termux / GitHub Mobile:
   - Using **GitHub Web UI** (in "Desktop site" mode on mobile browser): tap **uploading an existing file**, select the extracted project files (ensuring `.github/workflows/build.yml` is included), and tap **Commit changes**.
   - Or using **Termux** on Android:
     ```bash
     pkg install git unzip
     unzip ~/storage/downloads/smart-network-alert.zip -d ~/smart-network-alert
     cd ~/smart-network-alert
     git init
     git add .
     git commit -m "Initial commit: Smart Network Alert"
     git branch -M main
     git remote add origin https://github.com/<YOUR_USERNAME>/smart-network-alert.git
     git push -u origin main
     ```

### Step 2: Configure Optional Release Signing Secrets in GitHub
1. In your GitHub repository on mobile browser, go to **Settings** → **Secrets and variables** → **Actions**.
2. Add the following repository secrets (optional—if omitted, the workflow automatically generates an ephemeral keystore so `assembleRelease` still succeeds):
   - `RELEASE_KEYSTORE_BASE64`: Base64-encoded `.jks` keystore file (`base64 -w 0 my-upload-key.jks`).
   - `RELEASE_STORE_PASSWORD`: Your keystore password.
   - `RELEASE_KEY_PASSWORD`: Your key alias (`upload`) password.

### Step 3: Trigger Build & Download the APK on Your Phone
1. Tap the **Actions** tab in your GitHub repository.
2. Select the **Android CI & Signed Release Build** workflow.
3. Wait ~3–5 minutes for the workflow to complete Gradle wrapper validation, Android Lint, unit/Robolectric tests, `assembleDebug`, and `assembleRelease`.
4. Scroll down to the **Artifacts** section at the bottom of the completed workflow run and tap **`smart-network-alert-apks`** to download the ZIP to your phone.

### Step 4: Install the APK on Your Android Phone
1. Open the Android **Files** app and tap the downloaded `smart-network-alert-apks.zip`.
2. Extract **`app-debug.apk`** (or **`app-release.apk`** if signed with your release key).
3. Tap the `.apk` file. If prompted, allow **"Install unknown apps"** for your Files app.
4. Open **Smart Network Alert**, review the privacy rationale dialog, grant `READ_PHONE_STATE` and `POST_NOTIFICATIONS`, and toggle **Real-Time Cellular Monitoring** ON.

---

## Local Command-Line Build Instructions

```bash
# Run unit and Robolectric tests
./gradlew :app:testDebugUnitTest

# Run Android Lint static analysis
./gradlew :app:lintDebug

# Build Debug and Release APKs
./gradlew :app:assembleDebug :app:assembleRelease
```
