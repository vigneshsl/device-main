# Verification, Battery Profiling & Test Matrix (`TESTING.md`)

This document provides step-by-step instructions for running automated JVM/Robolectric tests, static analysis, battery consumption profiling via `adb shell dumpsys batterystats`, and the manual device/OS test matrix.

> **Truthfulness Note**: Automated Unit Tests, Robolectric UI/Security Tests, Android Lint, `assembleDebug`, and `assembleRelease` were executed and verified in the build container. Physical device radio handovers, physical OEM ROM tests (Pixel, Samsung, Xiaomi), and the 1-hour physical `adb shell dumpsys batterystats` idle run require physical hardware and are explicitly marked **Not Verified (Requires Physical Device / ADB)** in the pass/fail tables below.

---

## 1. Automated Unit, Robolectric & Static Analysis Suite

### Commands
```bash
# 1. Execute all Pure Kotlin Unit Tests & Robolectric Compose/Security Tests
gradle :app:testDebugUnitTest

# 2. Execute Android Lint Static Analysis
gradle :app:lintDebug

# 3. Verify Debug and R8-Minified Release Builds
gradle :app:assembleDebug :app:assembleRelease
```

### Automated Test Pass/Fail Table (Executed & Verified in Build Environment)

| Test ID | Suite / Class | Scenario Verified | Expected Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| **UT-01** | `ExampleUnitTest` | `maps 5G NR SA and all 5G NR override constants accurately` | `NETWORK_TYPE_NR`, `OVERRIDE_NETWORK_TYPE_NR_NSA`, `NR_NSA_MMWAVE`, `NR_ADVANCED` map to `5G`. | **PASS (Verified)** |
| **UT-02** | `ExampleUnitTest` | `maps LTE and LTE Advanced Pro variants to 4G` | `NETWORK_TYPE_LTE`, `LTE_CA`, `OVERRIDE_NETWORK_TYPE_LTE_CA`, `LTE_ADVANCED_PRO` map to `4G`. | **PASS (Verified)** |
| **UT-03** | `ExampleUnitTest` | `maps all 3G UMTS HSPA EVDO EHRPD TD_SCDMA constants to 3G` | All 10 3GPP/3GPP2 3G constants map to `3G`. | **PASS (Verified)** |
| **UT-04** | `ExampleUnitTest` | `maps all 2G GPRS EDGE GSM CDMA 1xRTT IDEN constants to 2G` | All 6 legacy 2G constants map to `2G`. | **PASS (Verified)** |
| **UT-05** | `ExampleUnitTest` | `maps UNKNOWN, IWLAN, missing permission, airplane mode, no SIM, and out-of-service to UNKNOWN` | Honest `Unknown` returned in all failure/unpermitted states; never fabricates readings. | **PASS (Verified)** |
| **UT-06** | `ExampleUnitTest` | `confirm transition only after debounce delay elapses` | `5G → 4G` does not fire at `2,999 ms`; fires at `3,000 ms` with exact timestamp & signal bars. | **PASS (Verified)** |
| **UT-07** | `ExampleUnitTest` | `rapid 5G to 4G to 5G revert within debounce window cancels alert` | Reversion at `1,500 ms` cancels coroutine delay; zero alerts emitted. | **PASS (Verified)** |
| **UT-08** | `ExampleUnitTest` | `flapping 5G and 4G repeatedly under debounce window suppresses all spam` | 5 rapid oscillations emit 0 alerts; final stabilization for `3,000 ms` emits 1 alert. | **PASS (Verified)** |
| **UT-09** | `ExampleUnitTest` | `unknown in between handles both return to 5G (silent) and handover to 4G (alerts 5G to 4G)` | `5G → Unknown → 5G` is silent; `5G → Unknown → 4G` emits `5G → 4G`. | **PASS (Verified)** |
| **UT-10** | `ExampleUnitTest` | `service restart with seedInitialConfirmedGeneration avoids duplicate baseline alert` | Seeding `4G` on service restart suppresses baseline callback; subsequent `5G` emits `4G → 5G`. | **PASS (Verified)** |
| **UT-11** | `ExampleUnitTest` | `settings serializer round-trips all fields and per-transition rules accurately` | All booleans, volume, debounce, custom sound metadata, and 12 transition rules round-trip. | **PASS (Verified)** |
| **UT-12** | `ExampleUnitTest` | `history serializer caps at 50 items and preserves order and metadata` | 60 events are capped at 50 items in exact order. | **PASS (Verified)** |
| **RT-01** | `ExampleRobolectricTest` | `permission denied shows Unknown state and clicking switch displays rationale dialog` | UI shows `Unknown`, switch is OFF, tapping switch opens `rationale_dialog_phone`. | **PASS (Verified)** |
| **RT-02** | `ExampleRobolectricTest` | `switch state and last change banner are restored after process death when permission is granted` | New `MainViewModel` over persisted `DataStore` restores ON switch and `"Network changed: 5G → 4G"`. | **PASS (Verified)** |
| **RT-03** | `ExampleRobolectricTest` | `custom sound validator rejects corrupt and oversized files and accepts valid WAV header` | Rejects corrupt header, non-audio MIME (`application/pdf`), and files `> 2 MB`. | **PASS (Verified)** |

---

## 2. 1-Hour Idle Battery Verification Methodology (`batterystats` & `dumpsys battery`)

### Why the App Achieves Negligible Idle Drain
1. **Zero Polling**: `NetworkMonitorService` registers `TelephonyCallback` with the system `TelephonyRegistry` binder service. The app process sleeps in `epoll_wait` until the modem baseband notifies `TelephonyRegistry` of a `TelephonyDisplayInfo`, `SignalStrength`, or `ServiceState` change.
2. **Zero WakeLocks**: No `PowerManager.WakeLock` is acquired at any point.
3. **Ephemeral Audio**: `AudioTrack` / `MediaPlayer` instances are allocated only during the ~300 ms alert playback window and immediately released.
4. **Static Notification**: The foreground notification is posted once in `onStartCommand` with `IMPORTANCE_LOW` and `setOnlyAlertOnce(true)`.

### Step-by-Step ADB Verification Procedure (For Physical Device QA)
Run the following commands on a physical Android 12–15 device with a live SIM card:

```bash
# 1. Connect device via USB, enable monitoring in Smart Network Alert, then reset batterystats:
adb shell dumpsys batterystats --reset
adb shell dumpsys batterystats --enable full-wake-history

# 2. Simulate unplugging USB charging so Android enters normal battery accounting and Doze:
adb shell dumpsys battery unplug
adb shell input keyevent KEYCODE_POWER   # Turn screen OFF

# 3. Leave device idle for 60 minutes (1 hour).

# 4. After 60 minutes, capture battery status and per-UID batterystats:
adb shell dumpsys battery > battery_snapshot_1hr.txt
adb shell dumpsys batterystats com.aistudio.smartnetworkalert.snakxm > batterystats_1hr.txt

# 5. Restore normal charging state:
adb shell dumpsys battery reset
```

### Expected Criteria & Verification Status
- **WakeLocks held (`Total wake lock time`)**: Expected `0 ms` (No wake locks exist in codebase).
- **CPU Time over 1 hour idle (`u0a... Cpu`)**: Expected `< 500 ms` total user+system CPU time across 60 minutes.
- **Alarms / Jobs triggered (`Wakeup alarms`)**: Expected `0`.
- **Verification Status in Container**: **Not Verified (No physical Android device or `adb` daemon exists in the cloud build container; architectural zero-polling/zero-wakelock invariants are verified by code inspection).**

---

## 3. Manual Hardware & OS Test Matrix

Because the cloud container has no cellular baseband modem or physical OEM devices, the matrix below provides the exact steps, expected results, and current verification status:

| Matrix ID | Environment / Scenario | Test Steps | Expected Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| **MM-01** | **Android 12 (API 31)** | Install APK, toggle Monitoring ON, grant `READ_PHONE_STATE`. | Monitoring starts immediately (no `POST_NOTIFICATIONS` prompt on API 31); static notification shown. | **Not Verified (Requires Physical Device)** |
| **MM-02** | **Android 13 (API 33)** | Install APK, toggle Monitoring ON. | Shows Phone State rationale → OS prompt → Notification rationale → OS prompt → Monitoring Running. | **Not Verified (Requires Physical Device)** |
| **MM-03** | **Android 14 (API 34)** | Start monitoring, verify `specialUse` FGS subtype compliance in `adb shell dumpsys activity services`. | Service runs under `foregroundServiceType=specialUse` without `MissingForegroundServiceTypeException`. | **Not Verified (Requires Physical Device)** |
| **MM-04** | **Android 15 (API 35)** | Verify edge-to-edge insets, `specialUse` FGS rules, and process death restoration. | UI respects status/navigation bars; Robolectric API 35 test passes; physical radio test pending hardware. | **Partially Verified (Robolectric SDK 35 PASS; Hardware Not Verified)** |
| **MM-05** | **Google Pixel (Stock Android)** | Drive/walk between 5G NR and 4G LTE coverage or toggle Preferred Network Type in `*#*#4636#*#*`. | After 3.0s debounce, plays Sonar Downgrade Pulse and logs `5G → 4G` in history. | **Not Verified (Requires Physical Pixel)** |
| **MM-06** | **Samsung OneUI** | Enable monitoring, turn screen off for 30 mins, trigger 5G → 4G drop; test Doze deep link. | Alert plays; if OneUI Deep Sleep restricts service, Diagnostics screen deep link opens battery settings. | **Not Verified (Requires Physical Samsung)** |
| **MM-07** | **Xiaomi HyperOS / MIUI** | Enable monitoring, test background persistence and custom audio file picker. | Custom audio ≤ 2 MB imports cleanly; Diagnostics guide explains MIUI battery saver exemption. | **Not Verified (Requires Physical Xiaomi)** |
| **MM-08** | **Wi-Fi Only (Cellular Data Connected in Background)** | Connect to Wi-Fi while SIM remains registered on 5G/4G cellular voice/data network. | Continues reporting cellular generation via `TelephonyDisplayInfo` (or `Unknown` if `NETWORK_TYPE_IWLAN` only). | **Unit Test Verified (`IWLAN` → `Unknown`); Hardware Not Verified** |
| **MM-09** | **No SIM Card Inserted** | Remove physical SIM / disable eSIM and open app. | Displays honest `Unknown` generation, 0/4 signal bars, and `"No Active SIM / No Cellular Service"` banner. | **Unit Test Verified; Hardware Not Verified** |
| **MM-10** | **Airplane Mode ON** | Enable Airplane Mode from Quick Settings. | Immediately transitions raw display to `Unknown`, cancels pending debounce timers, shows `"Airplane Mode Active"` banner. | **Unit Test Verified; Hardware Not Verified** |
| **MM-11** | **Dual SIM (Switch Default Data SIM)** | Insert 2 SIMs; switch Default Mobile Data SIM from SIM 1 to SIM 2 in Android Settings. | `ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED` re-binds `TelephonyCallback` to new `subId` and updates carrier pill. | **Not Verified (Requires Dual-SIM Hardware)** |
| **MM-12** | **Do Not Disturb (DND) ON** | Enable Do Not Disturb in Android Quick Settings. | Home and Diagnostics screens display `"Do Not Disturb is Active"` plain-language warning banner. | **Not Verified (Requires Physical Device)** |
| **MM-13** | **Battery Saver / Extreme Battery Saver** | Enable system Battery Saver mode. | Diagnostics banner warns if battery optimization is active; deep link opens exemption settings without forcing it. | **Not Verified (Requires Physical Device)** |
| **MM-14** | **Swipe App from Recents** | With Monitoring ON, open Recents and swipe Smart Network Alert away. | `NetworkMonitorService` (`START_STICKY` foreground service) remains active; reopening app restores ON switch and history. | **State Restoration Verified in Robolectric; Hardware Not Verified** |
| **MM-15** | **Device Reboot** | Reboot phone while Monitoring is ON. | App does **not** auto-start on boot (by explicit privacy/Android 15 design, no `RECEIVE_BOOT_COMPLETED`). Opening app resumes monitoring. | **Design Verified in Manifest; Hardware Not Verified** |
