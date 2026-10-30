package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.AlertSettings
import com.example.domain.BuiltInTone
import com.example.domain.NetworkGeneration
import com.example.domain.SignalLevel
import com.example.domain.SettingsSerializer
import com.example.domain.TransitionEvent
import com.example.domain.TransitionKey
import com.example.domain.TransitionRuleConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map

private val Context.smartNetworkDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "smart_network_alert_prefs"
)

/**
 * Live cellular runtime telemetry shared between the event-driven [NetworkMonitorService]
 * and the UI ViewModel without polling.
 */
data class LiveRadioTelemetry(
    val currentGeneration: NetworkGeneration = NetworkGeneration.UNKNOWN,
    val lastConfirmedGeneration: NetworkGeneration = NetworkGeneration.UNKNOWN,
    val pendingTargetGeneration: NetworkGeneration? = null,
    val signalLevel: SignalLevel = SignalLevel.NONE_OR_UNKNOWN,
    val isServiceRunning: Boolean = false,
    val activeSimCarrierName: String = "Default Data SIM",
    val activeSubId: Int = -1,
    val hasPhonePermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val isAirplaneModeOn: Boolean = false,
    val hasActiveSim: Boolean = true,
    val isInService: Boolean = true,
    val isDoNotDisturbActive: Boolean = false,
    val isBatteryOptimizationActive: Boolean = true
)

/**
 * Single source of truth for [AlertSettings], [TransitionEvent] history (capped at 50 items),
 * and live event-driven radio telemetry.
 */
class NetworkMonitorRepository(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val KEY_SETTINGS = stringPreferencesKey("serialized_alert_settings_v1")
        private val KEY_HISTORY = stringPreferencesKey("serialized_transition_history_v1")

        @Volatile
        private var INSTANCE: NetworkMonitorRepository? = null

        fun getInstance(context: Context): NetworkMonitorRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkMonitorRepository(
                    context.applicationContext.smartNetworkDataStore
                ).also { INSTANCE = it }
            }
        }
    }

    private val _liveTelemetry = MutableStateFlow(LiveRadioTelemetry())
    val liveTelemetry: StateFlow<LiveRadioTelemetry> = _liveTelemetry.asStateFlow()

    val settingsFlow: Flow<AlertSettings> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_SETTINGS].orEmpty()
        SettingsSerializer.deserializeSettings(raw)
    }

    val historyFlow: Flow<List<TransitionEvent>> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_HISTORY].orEmpty()
        SettingsSerializer.deserializeHistory(raw)
    }

    fun updateLiveTelemetry(transform: (LiveRadioTelemetry) -> LiveRadioTelemetry) {
        _liveTelemetry.update(transform)
    }

    suspend fun updateSettings(transform: (AlertSettings) -> AlertSettings) {
        dataStore.edit { prefs ->
            val current = SettingsSerializer.deserializeSettings(prefs[KEY_SETTINGS].orEmpty())
            val updated = transform(current)
            prefs[KEY_SETTINGS] = SettingsSerializer.serializeSettings(updated)
        }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        updateSettings {
            it.copy(
                monitoringEnabled = enabled,
                masterSoundEnabled = if (enabled) true else it.masterSoundEnabled
            )
        }
    }

    suspend fun setMasterSoundEnabled(enabled: Boolean) {
        updateSettings { it.copy(masterSoundEnabled = enabled) }
    }

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        updateSettings { it.copy(hapticFeedbackEnabled = enabled) }
    }

    suspend fun setAlertVolumePercent(percent: Int) {
        updateSettings { it.copy(alertVolumePercent = percent.coerceIn(0, 100)) }
    }

    suspend fun setDebounceMillis(millis: Long) {
        updateSettings { it.copy(debounceMillis = millis.coerceIn(500L, 15_000L)) }
    }

    suspend fun setUseRecoverySoundOnUpgrade(enabled: Boolean) {
        updateSettings { it.copy(useRecoverySoundOnUpgrade = enabled) }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        updateSettings { it.copy(dynamicColorEnabled = enabled) }
    }

    suspend fun setCustomSoundMetadata(internalFileName: String?, displayName: String?) {
        updateSettings {
            it.copy(
                customSoundFileName = internalFileName,
                customSoundDisplayName = displayName
            )
        }
    }

    suspend fun updateTransitionRule(key: TransitionKey, enabled: Boolean, tone: BuiltInTone) {
        updateSettings { current ->
            val updatedMap = current.transitionRules.toMutableMap()
            updatedMap[key] = TransitionRuleConfig(key = key, enabled = enabled, tone = tone)
            current.copy(transitionRules = updatedMap)
        }
    }

    suspend fun recordTransitionEvent(event: TransitionEvent) {
        dataStore.edit { prefs ->
            val existing = SettingsSerializer.deserializeHistory(prefs[KEY_HISTORY].orEmpty())
            val updated = (listOf(event) + existing).take(SettingsSerializer.MAX_HISTORY_ITEMS)
            prefs[KEY_HISTORY] = SettingsSerializer.serializeHistory(updated)
        }
    }

    suspend fun clearHistory() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_HISTORY)
        }
    }
}
