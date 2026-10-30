# Privacy Policy for Smart Network Alert

**Last updated**: October 28, 2026

**Smart Network Alert** ("the App") is an offline-first Android utility designed to monitor cellular network generation transitions (5G ↔ 4G ↔ 3G ↔ 2G) on your device's active data SIM and trigger customizable acoustic and haptic alerts.

---

## 1. Zero Data Collection & Zero Exfiltration

- **No Internet Access**: The App does **not** declare the `android.permission.INTERNET` permission in its Android Manifest. It is mathematically and kernel-enforced impossible for the App to transmit any data over any network interface.
- **No Personal Identifiers**: The App never reads, accesses, or collects phone numbers, IMEI, IMSI, MAC addresses, device serial numbers, contacts, or accounts.
- **No Location Tracking**: The App does not request `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION`. Cell tower identifiers (CID/LAC) are completely ignored.

---

## 2. On-Device Radio Telemetry Usage

The App requests the `READ_PHONE_STATE` runtime permission solely to subscribe to on-device cellular radio callbacks via Android's official `TelephonyCallback` API (`TelephonyCallback.DisplayInfoListener`, `SignalStrengthsListener`, and `ServiceStateListener`).

This telemetry is processed entirely in local device memory to:
1. Display the current cellular generation (5G, 4G, 3G, 2G, or Unknown) and signal bars (0–4) in the App UI.
2. Detect confirmed generation transitions (e.g., 5G → 4G) after a user-configured stabilization debounce delay.
3. Play local alert tones when a transition occurs.

---

## 3. Local Data Storage & User Control

- **Transition History**: Transition events (source generation, destination generation, timestamp, and signal level) are saved locally on your device using encrypted `androidx.datastore` Preferences. Up to 50 items are retained.
- **Data Deletion**: You can clear all recorded history at any time by tapping **Clear History** on the Home screen or clearing App Data in Android System Settings.

---

## 4. Contact Information

If you have questions about this Privacy Policy, please contact the developer via the official repository support page.
