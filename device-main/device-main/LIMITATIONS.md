# Known Platform & Hardware Limitations (`LIMITATIONS.md`)

In accordance with engineering transparency and accuracy-first design, this document details real-world Android platform, carrier, and OEM constraints affecting cellular generation monitoring.

---

## 1. Carrier & Device Variation in 5G Reporting (`TelephonyDisplayInfo`)

- **5G Non-Standalone (NSA) Anchor Behavior**: On 5G NSA networks (EN-DC), the device remains anchored to an LTE (4G) control channel (`networkType == NETWORK_TYPE_LTE`) while aggregating a 5G NR carrier. Android reports this via `TelephonyDisplayInfo.getOverrideNetworkType()` (`OVERRIDE_NETWORK_TYPE_NR_NSA` or `OVERRIDE_NETWORK_TYPE_NR_ADVANCED`).
- **UpperLayerIndication (SIB2) vs. Active NR Bearers**: Some carriers configure their LTE towers to broadcast the 5G `upperLayerIndication-r15` bit in System Information Block 2 (SIB2) even before an active 5G NR radio bearer is allocated, causing the Android OS status bar and `TelephonyDisplayInfo` to report `OVERRIDE_NETWORK_TYPE_NR_NSA` while the modem is idling on an LTE anchor cell. Conversely, during idle screen-off states, the modem may release the secondary 5G NR carrier to save power and temporarily report `4G (LTE)` until data transfer resumes.
- **Why Debounce Exists**: To prevent alert fatigue when a modem rapidly attaches and detaches secondary 5G NSA carriers at cell edges, Smart Network Alert uses a configurable coroutine debounce window (default **3.0 seconds**, adjustable up to **15.0 seconds**) that cancels the alert if the radio reverts before the timer completes.

---

## 2. OEM Background Process Kills (Samsung, Xiaomi, OnePlus, etc.)

- While Smart Network Alert uses a compliant Android Foreground Service (`NetworkMonitorService`) with zero wake locks and zero polling, certain OEM Android distributions (such as Xiaomi HyperOS/MIUI, Samsung OneUI "Deep Sleeping Apps", and OnePlus/Oppo ColorOS) implement proprietary background process killers that can terminate even active foreground services during extended screen-off periods (`DontKillMyApp`).
- **Mitigation & User Control**: The app includes a dedicated **Diagnostics & Battery** screen that detects whether standard or OEM battery optimization is active (`PowerManager.isIgnoringBatteryOptimizations`) and provides a non-forced deep link (`Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`) so users on aggressive OEM ROMs can optionally set the app to **Unrestricted**.

---

## 3. Android 14 (API 34) & Android 15 (API 35) Foreground Service Rules

- Starting in Android 14 (`targetSdk 34+`) and enforced strictly in Android 15 (`targetSdk 35`), every foreground service must declare an explicit `android:foregroundServiceType` and corresponding permission.
- Because continuous real-time cellular generation monitoring (`TelephonyCallback`) is event-driven radio telemetry rather than a media player, navigation session, or health tracker, `NetworkMonitorService` uses `FOREGROUND_SERVICE_SPECIAL_USE` (`foregroundServiceType="specialUse"`) paired with the mandatory `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" ... />` justification in `AndroidManifest.xml`.
- **No Auto-Start on Boot**: Android 15 restricts starting specific foreground service types from `BOOT_COMPLETED` broadcast receivers in the background. Furthermore, to respect user control and battery life, Smart Network Alert **intentionally does not implement auto-start on device reboot (`RECEIVE_BOOT_COMPLETED`)**. After a full phone reboot, the user must open the app or toggle Monitoring ON to resume the foreground service.

---

## 4. Do Not Disturb (DND) & System Audio Stream Policies

- Alert tones are played using `AudioAttributes.USAGE_NOTIFICATION_EVENT` and `CONTENT_TYPE_SONIFICATION` so they respect the user's system notification/event audio routing.
- When **Do Not Disturb (DND)** (`NotificationManager.INTERRUPTION_FILTER_PRIORITY`, `ALARMS`, or `NONE`) or system mute/vibrate mode is active, Android's audio policy manager may attenuate or silence `USAGE_NOTIFICATION_EVENT` audio streams.
- **Plain-Language Visibility**: Smart Network Alert never overrides or bypasses the user's Do Not Disturb settings (`ACCESS_NOTIFICATION_POLICY` is not requested). Instead, it detects active DND filters in real time via `NotificationManager.getCurrentInterruptionFilter()` and displays a clear **"Do Not Disturb is Active"** banner on the Home and Diagnostics screens.
