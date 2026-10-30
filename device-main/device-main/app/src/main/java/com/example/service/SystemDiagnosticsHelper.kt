package com.example.service

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/**
 * Reads system-level diagnostics (permissions, Airplane Mode, SIM state, Do Not Disturb,
 * Battery Optimization, and Remove Animations accessibility flag) on demand without polling.
 */
object SystemDiagnosticsHelper {

    fun hasPhoneStatePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isAirplaneModeOn(context: Context): Boolean {
        return runCatching {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                0
            ) != 0
        }.getOrDefault(false)
    }

    fun isDoNotDisturbActive(context: Context): Boolean {
        return runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val filter = nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
            filter != NotificationManager.INTERRUPTION_FILTER_ALL
        }.getOrDefault(false)
    }

    fun isBatteryOptimizationActive(context: Context): Boolean {
        return runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            // If ignoring battery optimizations is true, then optimization is NOT active
            val ignoring = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            !ignoring
        }.getOrDefault(true)
    }

    fun isRemoveAnimationsEnabled(context: Context): Boolean {
        return runCatching {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            scale == 0.0f
        }.getOrDefault(false)
    }

    /**
     * Resolves the default data subscription ID and human-readable carrier label for Dual-SIM clarity.
     */
    fun resolveDefaultDataSubscriptionInfo(context: Context): Pair<Int, String> {
        val defaultDataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val subTm = if (defaultDataSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && tm != null) {
            tm.createForSubscriptionId(defaultDataSubId)
        } else {
            tm
        }

        val simReady = subTm?.simState == TelephonyManager.SIM_STATE_READY
        val operatorName = subTm?.networkOperatorName?.takeIf { it.isNotBlank() }
            ?: subTm?.simOperatorName?.takeIf { it.isNotBlank() }

        // Subscription IDs are not slot numbers; resolve the physical slot for a "SIM 1 / SIM 2" label.
        val slotIndex = if (defaultDataSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            SubscriptionManager.getSlotIndex(defaultDataSubId)
        } else {
            SubscriptionManager.INVALID_SIM_SLOT_INDEX
        }
        val simName = if (slotIndex >= 0) "SIM ${slotIndex + 1}" else "SIM"

        val label = when {
            !simReady -> "No SIM"
            operatorName != null -> "$simName ($operatorName)"
            else -> simName
        }
        return defaultDataSubId to label
    }

    fun hasActiveSim(context: Context): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return false
        return tm.simState == TelephonyManager.SIM_STATE_READY
    }
}
