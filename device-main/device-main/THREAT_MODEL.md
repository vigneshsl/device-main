# Threat Model — Smart Network Alert

## 1. System Overview & Trust Boundaries

**Smart Network Alert** is an offline-first, zero-network Android utility that listens to OS cellular radio state callbacks (`TelephonyCallback.DisplayInfoListener` and `SignalStrengthsListener`) and plays an acoustic/haptic alert upon stabilized cellular generation transitions.

- **Trust Boundary 1 (OS Binder ↔ App Process)**: `TelephonyManager` delivers `TelephonyDisplayInfo`, `SignalStrength`, and `ServiceState` over binder IPC when `READ_PHONE_STATE` is granted.
- **Trust Boundary 2 (External Storage / Document Picker ↔ App Sandbox)**: User selects an arbitrary file via Android's Storage Access Framework (`ActivityResultContracts.OpenDocument`). The content stream is untrusted until validated and copied into app-private storage (`Context.getFilesDir()`).
- **Trust Boundary 3 (Other Installed Apps ↔ App Components)**: Third-party apps on the same device attempting to invoke services, read local history via backup/extraction, or scrape `logcat`.

---

## 2. Protected Assets

| Asset ID | Asset Name | Sensitivity | Description |
| :--- | :--- | :--- | :--- |
| **A1** | Cellular Transition History & Timestamps | Medium | Local record (up to 50 entries) of when the user's device switched between 5G, 4G, 3G, and 2G, which could imply coarse movement patterns (e.g., entering a subway or rural area). |
| **A2** | Live Cellular Radio State | Medium | Real-time generation (`5G`/`4G`/`3G`/`2G`/`Unknown`) and signal bars (`0–4`) observed via `READ_PHONE_STATE`. |
| **A3** | App Process Integrity & Availability | High | The stability, memory bounds, and battery consumption of `NetworkMonitorService` and the UI process. |
| **A4** | User Custom Alert Audio File | Low | Audio clip imported by the user into app-private storage (`filesDir/custom_alerts/`). |

---

## 3. Attackers, Threat Vectors & Mitigations

### Threat T1: Malicious Co-Installed App (IPC Spoofing / Service Abuse / Logcat Scraping)
- **Attack Vector**: Another unprivileged app installed on the user's phone attempts to start, stop, or bind to `NetworkMonitorService` with crafted Intents, or reads `logcat` to harvest cellular transition history without holding `READ_PHONE_STATE`.
- **Mitigations Implemented**:
  1. **Strict Component Isolation**: `NetworkMonitorService` is declared with `android:exported="false"` in `AndroidManifest.xml`. Only `MainActivity` (`MAIN`/`LAUNCHER`) is exported.
  2. **Non-Exported Dynamic Receivers**: Dynamic `BroadcastReceiver` in `NetworkMonitorService` is registered with `ContextCompat.RECEIVER_NOT_EXPORTED`.
  3. **Zero Release Logging**: Network transitions and radio states are never logged to `android.util.Log`. Furthermore, `proguard-rules.pro` strips `Log.v`, `Log.d`, and `Log.i` calls across the entire bytecode during R8 release builds.
  4. **Zero Network Exfiltration**: The app does not declare `android.permission.INTERNET` in `AndroidManifest.xml`, making network exfiltration impossible even at the OS kernel socket level.

### Threat T2: Malformed, Corrupt, or Oversized Audio File (DoS / Parser Exploitation / URI Persistence)
- **Attack Vector**: A user (or a malicious `DocumentProvider` app) supplies a multi-gigabyte file (zip bomb / OOM DoS), a non-audio executable disguised as `.mp3`, a malformed media container targeting `MediaPlayer`/`Stagefright` codecs, or a transient `content://` URI whose permission later expires or points to sensitive files.
- **Mitigations Implemented**:
  1. **No Raw External URI Persistence**: `CustomSoundStorage` never saves external `content://` or `file://` URIs in `DataStore`. It copies validated bytes into `context.filesDir/custom_alerts/validated_custom_alert_sound.bin`.
  2. **MIME Type Allowlist**: Rejects any URI whose `ContentResolver.getType(uri)` does not start with `audio/` or match `application/ogg` / `application/flac`.
  3. **Dual-Layer 2 MB Size Cap (`MAX_SOUND_BYTES = 2,097,152`)**: Checks `OpenableColumns.SIZE` metadata first, AND enforces a hard byte counter inside the `8 KB` streaming copy loop so a lying `ContentProvider` is aborted and deleted the instant it exceeds `2 MB`.
  4. **Container Magic Header & `MediaExtractor` Track Verification**: Verifies the first 12 bytes against known safe audio magic signatures (`RIFF`, `OggS`, `fLaC`, `ID3`, MP3 frame sync, ISO `ftyp`) and parses the file with `MediaExtractor` to confirm at least one valid `audio/*` track exists before accepting the file. Failed files are unlinked immediately in `finally`/`catch`.
  5. **Path Traversal Sanitization**: `getCustomSoundFile()` strips directory separators via `File(internalFileName).name` before resolving inside `filesDir/custom_alerts/`.

### Threat T3: Physical or Cloud Backup Extraction (`adb backup` / Cloud Auto-Backup / Device-to-Device Transfer)
- **Attack Vector**: An attacker with temporary USB debugging access (`adb backup`) or access to the user's cloud backup account attempts to extract `smart_network_alert_prefs.preferences_pb` (containing transition timestamps) or private files.
- **Mitigations Implemented**:
  1. **`android:allowBackup="false"`**: Explicitly disabled in `<application>` inside `AndroidManifest.xml`.
  2. **Explicit Exclusion Rules**: Both `res/xml/data_extraction_rules.xml` (`<cloud-backup>` and `<device-transfer>`) and `res/xml/backup_rules.xml` (`<full-backup-content>`) explicitly `<exclude>` all domains (`root`, `file`, `database`, `sharedpref`, `external`).
  3. **Zero PII Stored**: The app never queries `getLine1Number()`, `getImei()`, `getSubscriberId()`, or `ACCESS_FINE_LOCATION` (cell tower CID/LAC coordinates). Only coarse generation labels (`5G`/`4G`/`3G`/`2G`) and epoch timestamps are stored.
