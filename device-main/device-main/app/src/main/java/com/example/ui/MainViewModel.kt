package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.telephony.SubscriptionManager
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.CustomSoundImportResult
import com.example.data.CustomSoundStorage
import com.example.data.LiveRadioTelemetry
import com.example.data.NetworkMonitorRepository
import com.example.domain.AlertSettings
import com.example.domain.BuiltInTone
import com.example.domain.NetworkGeneration
import com.example.domain.NetworkGenerationMapper
import com.example.domain.SignalLevel
import com.example.domain.TransitionEvent
import com.example.domain.TransitionKey
import com.example.service.AlertAudioPlayer
import com.example.service.NetworkMonitorService
import com.example.service.SystemDiagnosticsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Represents the explicit 3-way state of the monitoring switch required by the specification:
 * - RUNNING
 * - STOPPED
 * - PERMISSION_NEEDED
 */
enum class MonitoringSwitchState {
    RUNNING,
    STOPPED,
    PERMISSION_NEEDED
}

/**
 * Represents which rationale dialog should be presented before launching the OS permission prompt.
 */
enum class PendingPermissionRationale {
    NONE,
    READ_PHONE_STATE,
    POST_NOTIFICATIONS
}

/**
 * Combined UI state for the Smart Network Alert Compose interface.
 */
data class MainUiState(
    val settings: AlertSettings = AlertSettings(),
    val telemetry: LiveRadioTelemetry = LiveRadioTelemetry(),
    val history: List<TransitionEvent> = emptyList(),
    val monitoringSwitchState: MonitoringSwitchState = MonitoringSwitchState.STOPPED,
    val pendingRationale: PendingPermissionRationale = PendingPermissionRationale.NONE,
    val statusMessage: String? = null,
    val removeAnimationsEnabled: Boolean = false
) {
    val lastTransition: TransitionEvent?
        get() = history.firstOrNull()
}

class MainViewModel(
    private val appContext: Context,
    private val repository: NetworkMonitorRepository,
    private val customSoundStorage: CustomSoundStorage,
    private val audioPlayer: AlertAudioPlayer
) : ViewModel() {

    private val _pendingRationale = MutableStateFlow(PendingPermissionRationale.NONE)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _removeAnimations = MutableStateFlow(false)

    val uiState: StateFlow<MainUiState> = combine(
        repository.settingsFlow,
        repository.liveTelemetry,
        repository.historyFlow,
        _pendingRationale,
        _statusMessage
    ) { settings, telemetry, history, rationale, message ->
        val switchState = when {
            !telemetry.hasPhonePermission && settings.monitoringEnabled ->
                MonitoringSwitchState.PERMISSION_NEEDED
            !telemetry.hasPhonePermission ->
                MonitoringSwitchState.PERMISSION_NEEDED
            settings.monitoringEnabled && telemetry.isServiceRunning ->
                MonitoringSwitchState.RUNNING
            settings.monitoringEnabled ->
                MonitoringSwitchState.RUNNING
            else ->
                MonitoringSwitchState.STOPPED
        }
        MainUiState(
            settings = settings,
            telemetry = telemetry,
            history = history,
            monitoringSwitchState = switchState,
            pendingRationale = rationale,
            statusMessage = message,
            removeAnimationsEnabled = _removeAnimations.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState()
    )

    init {
        refreshSystemDiagnosticsSnapshot()
    }

    /**
     * Reads current system permissions, Airplane Mode, Dual-SIM carrier label, DND, and one-shot
     * initial radio generation snapshot when the UI resumes or permissions change.
     */
    @SuppressLint("MissingPermission")
    fun refreshSystemDiagnosticsSnapshot() {
        val hasPhonePermImmediate = SystemDiagnosticsHelper.hasPhoneStatePermission(appContext)
        val hasNotifPermImmediate = SystemDiagnosticsHelper.hasNotificationPermission(appContext)
        repository.updateLiveTelemetry { current ->
            current.copy(
                hasPhonePermission = hasPhonePermImmediate,
                hasNotificationPermission = hasNotifPermImmediate
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val hasPhonePerm = SystemDiagnosticsHelper.hasPhoneStatePermission(appContext)
            val hasNotifPerm = SystemDiagnosticsHelper.hasNotificationPermission(appContext)
            val airplaneOn = SystemDiagnosticsHelper.isAirplaneModeOn(appContext)
            val dndActive = SystemDiagnosticsHelper.isDoNotDisturbActive(appContext)
            val batteryOpt = SystemDiagnosticsHelper.isBatteryOptimizationActive(appContext)
            val hasSim = SystemDiagnosticsHelper.hasActiveSim(appContext)
            val (subId, simLabel) = SystemDiagnosticsHelper.resolveDefaultDataSubscriptionInfo(appContext)
            _removeAnimations.value = SystemDiagnosticsHelper.isRemoveAnimationsEnabled(appContext)

            var oneShotGen = NetworkGeneration.UNKNOWN
            var oneShotSignal = SignalLevel.NONE_OR_UNKNOWN
            if (hasPhonePerm && !airplaneOn && hasSim) {
                runCatching {
                    val rootTm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    val tm = if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && rootTm != null) {
                        rootTm.createForSubscriptionId(subId)
                    } else {
                        rootTm
                    }
                    val netType = tm?.dataNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN
                    oneShotGen = NetworkGenerationMapper.map(
                        networkType = netType,
                        overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                        hasPermission = true,
                        isAirplaneModeOn = false,
                        hasActiveSim = true,
                        isInService = true
                    )
                    val sigLevel = tm?.signalStrength?.level ?: 0
                    oneShotSignal = SignalLevel.fromLevel(sigLevel)
                }
            }

            repository.updateLiveTelemetry { current ->
                val effectiveGen = if (current.isServiceRunning && current.currentGeneration != NetworkGeneration.UNKNOWN) {
                    current.currentGeneration
                } else if (!hasPhonePerm || airplaneOn || !hasSim) {
                    NetworkGeneration.UNKNOWN
                } else {
                    oneShotGen
                }
                val effectiveSignal = if (current.isServiceRunning && current.signalLevel != SignalLevel.NONE_OR_UNKNOWN) {
                    current.signalLevel
                } else if (!hasPhonePerm || airplaneOn || !hasSim) {
                    SignalLevel.NONE_OR_UNKNOWN
                } else {
                    oneShotSignal
                }

                current.copy(
                    currentGeneration = effectiveGen,
                    signalLevel = effectiveSignal,
                    hasPhonePermission = hasPhonePerm,
                    hasNotificationPermission = hasNotifPerm,
                    isAirplaneModeOn = airplaneOn,
                    hasActiveSim = hasSim,
                    activeSubId = subId,
                    activeSimCarrierName = simLabel,
                    isDoNotDisturbActive = dndActive,
                    isBatteryOptimizationActive = batteryOpt
                )
            }

            val currentSettings = uiState.value.settings
            if (currentSettings.monitoringEnabled && hasPhonePerm && !repository.liveTelemetry.value.isServiceRunning) {
                NetworkMonitorService.startService(appContext)
            }
        }
    }

    fun onToggleMonitoringClicked(desiredEnabled: Boolean) {
        if (!desiredEnabled) {
            viewModelScope.launch {
                repository.setMonitoringEnabled(false)
                NetworkMonitorService.stopService(appContext)
                repository.updateLiveTelemetry { it.copy(isServiceRunning = false, pendingTargetGeneration = null) }
            }
            return
        }

        // Check READ_PHONE_STATE first; show rationale before requesting
        if (!SystemDiagnosticsHelper.hasPhoneStatePermission(appContext)) {
            _pendingRationale.value = PendingPermissionRationale.READ_PHONE_STATE
            return
        }

        // Check POST_NOTIFICATIONS on Android 13+; show rationale if not yet granted
        if (!SystemDiagnosticsHelper.hasNotificationPermission(appContext)) {
            _pendingRationale.value = PendingPermissionRationale.POST_NOTIFICATIONS
            return
        }

        startMonitoringNow()
    }

    fun requestPhonePermissionRationale() {
        _pendingRationale.value = PendingPermissionRationale.READ_PHONE_STATE
    }

    fun requestNotificationPermissionRationale() {
        _pendingRationale.value = PendingPermissionRationale.POST_NOTIFICATIONS
    }

    fun dismissRationale() {
        _pendingRationale.value = PendingPermissionRationale.NONE
    }

    fun onPhonePermissionResult(granted: Boolean) {
        _pendingRationale.value = PendingPermissionRationale.NONE
        refreshSystemDiagnosticsSnapshot()
        if (granted) {
            if (!SystemDiagnosticsHelper.hasNotificationPermission(appContext)) {
                _pendingRationale.value = PendingPermissionRationale.POST_NOTIFICATIONS
            } else {
                startMonitoringNow()
            }
        } else {
            viewModelScope.launch {
                repository.setMonitoringEnabled(false)
                _statusMessage.value = appContext.getString(R.string.msg_permission_denied)
            }
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        _pendingRationale.value = PendingPermissionRationale.NONE
        refreshSystemDiagnosticsSnapshot()
        if (SystemDiagnosticsHelper.hasPhoneStatePermission(appContext)) {
            startMonitoringNow()
            if (!granted) {
                _statusMessage.value = appContext.getString(R.string.msg_notification_denied)
            }
        }
    }

    private fun startMonitoringNow() {
        viewModelScope.launch {
            repository.setMonitoringEnabled(true)
            repository.setMasterSoundEnabled(true)
            NetworkMonitorService.startService(appContext)
            refreshSystemDiagnosticsSnapshot()
        }
    }

    fun previewActiveAlertSound() {
        viewModelScope.launch {
            val settings = uiState.value.settings
            val tone = if (settings.customSoundFileName != null) {
                BuiltInTone.CUSTOM_FILE
            } else {
                BuiltInTone.RADAR_CHIME
            }
            audioPlayer.playAlert(
                tone = tone,
                volumePercent = settings.alertVolumePercent.coerceAtLeast(25),
                customSoundFileName = settings.customSoundFileName,
                triggerHaptics = settings.hapticFeedbackEnabled
            )
        }
    }

    fun setMasterSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setMasterSoundEnabled(enabled) }
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setHapticFeedbackEnabled(enabled) }
    }

    fun setAlertVolumePercent(percent: Int) {
        viewModelScope.launch { repository.setAlertVolumePercent(percent) }
    }

    fun setDebounceMillis(millis: Long) {
        viewModelScope.launch { repository.setDebounceMillis(millis) }
    }

    fun setUseRecoverySoundOnUpgrade(enabled: Boolean) {
        viewModelScope.launch { repository.setUseRecoverySoundOnUpgrade(enabled) }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setDynamicColorEnabled(enabled) }
    }

    fun updateTransitionRule(key: TransitionKey, enabled: Boolean, tone: BuiltInTone) {
        viewModelScope.launch { repository.updateTransitionRule(key, enabled, tone) }
    }

    fun testTransitionRuleTone(key: TransitionKey) {
        viewModelScope.launch {
            val settings = uiState.value.settings
            val rule = settings.transitionRules[key]
            val tone = if (settings.useRecoverySoundOnUpgrade && key.isUpgrade && rule?.tone == key.defaultTone) {
                BuiltInTone.RECOVERY_UPGRADE
            } else {
                rule?.tone ?: key.defaultTone
            }
            audioPlayer.playAlert(
                tone = tone,
                volumePercent = settings.alertVolumePercent.coerceAtLeast(25),
                customSoundFileName = settings.customSoundFileName,
                triggerHaptics = settings.hapticFeedbackEnabled
            )
        }
    }

    fun importCustomSound(uri: Uri) {
        viewModelScope.launch {
            when (val result = customSoundStorage.importFromUri(uri)) {
                is CustomSoundImportResult.Success -> {
                    repository.setCustomSoundMetadata(
                        internalFileName = result.internalFileName,
                        displayName = result.displayName
                    )
                    _statusMessage.value = appContext.getString(R.string.msg_sound_imported, result.displayName)
                }
                is CustomSoundImportResult.Rejected -> {
                    _statusMessage.value = appContext.getString(R.string.msg_sound_rejected)
                }
            }
        }
    }

    fun removeCustomSound() {
        viewModelScope.launch {
            customSoundStorage.deleteCustomSound()
            repository.setCustomSoundMetadata(null, null)
            _statusMessage.value = appContext.getString(R.string.msg_sound_removed)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _statusMessage.value = appContext.getString(R.string.msg_history_cleared)
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appCtx = context.applicationContext
            val repo = NetworkMonitorRepository.getInstance(appCtx)
            val storage = CustomSoundStorage(appCtx)
            val player = AlertAudioPlayer(appCtx, storage)
            return MainViewModel(appCtx, repo, storage, player) as T
        }
    }
}
