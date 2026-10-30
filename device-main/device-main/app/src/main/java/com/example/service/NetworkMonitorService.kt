package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.CustomSoundStorage
import com.example.data.NetworkMonitorRepository
import com.example.domain.AlertSettings
import com.example.domain.Clock
import com.example.domain.NetworkGeneration
import com.example.domain.NetworkGenerationMapper
import com.example.domain.NetworkTransitionStateMachine
import com.example.domain.SignalLevel
import com.example.domain.TransitionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Event-driven Foreground Service that monitors cellular generation changes on the default data
 * subscription using [TelephonyManager.registerTelephonyCallback].
 *
 * Battery & Architecture guarantees:
 * 1. Event-driven only: zero polling, zero timers/loops, zero WakeLocks, zero AlarmManager/WorkManager.
 * 2. Static notification: built and posted once when monitoring starts; never updated per-second.
 * 3. Unregisters all callbacks and stops itself immediately when monitoring is toggled OFF.
 * 4. Dual-SIM aware: listens to `SubscriptionManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED` and
 *    re-binds the [TelephonyCallback] to `telephonyManager.createForSubscriptionId(defaultDataSubId)`.
 */
class NetworkMonitorService : Service() {

    companion object {
        const val ACTION_START_MONITORING = "com.example.action.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.example.action.STOP_MONITORING"
        const val ACTION_DEFAULT_DATA_SUB_CHANGED = "android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED"
        private const val NOTIFICATION_CHANNEL_ID = "smart_network_monitor_static_channel"
        private const val NOTIFICATION_ID = 4101

        fun startService(context: Context) {
            val intent = Intent(context, NetworkMonitorService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, NetworkMonitorService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            runCatching { context.stopService(intent) }
        }
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)

    private lateinit var repository: NetworkMonitorRepository
    private lateinit var audioPlayer: AlertAudioPlayer
    private lateinit var stateMachine: NetworkTransitionStateMachine

    @Volatile
    private var latestSettings: AlertSettings = AlertSettings()

    private var boundTelephonyManager: TelephonyManager? = null
    private var registeredCallback: CellularCallback? = null
    private var isReceiverRegistered = false

    private var lastDisplayInfo: TelephonyDisplayInfo? = null
    private var lastServiceState: Int = ServiceState.STATE_IN_SERVICE

    private val systemStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_AIRPLANE_MODE_CHANGED,
                ACTION_DEFAULT_DATA_SUB_CHANGED,
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                    rebindTelephonyCallbackIfPermitted()
                    reevaluateCurrentGeneration()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = NetworkMonitorRepository.getInstance(applicationContext)
        audioPlayer = AlertAudioPlayer(
            context = applicationContext,
            customSoundStorage = CustomSoundStorage(applicationContext)
        )

        stateMachine = NetworkTransitionStateMachine(
            scope = serviceScope,
            clock = Clock.System,
            debounceMillisProvider = { latestSettings.debounceMillis },
            onStateSnapshotChanged = { snapshot ->
                repository.updateLiveTelemetry { current ->
                    current.copy(
                        currentGeneration = snapshot.currentRawGeneration,
                        lastConfirmedGeneration = snapshot.lastConfirmedGeneration,
                        pendingTargetGeneration = snapshot.pendingTargetGeneration
                    )
                }
            },
            onConfirmedTransition = { event ->
                handleConfirmedTransition(event)
            }
        )

        serviceScope.launch {
            repository.settingsFlow.collectLatest { settings ->
                latestSettings = settings
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_MONITORING) {
            stopMonitoringAndSelf()
            return START_NOT_STICKY
        }

        if (!SystemDiagnosticsHelper.hasPhoneStatePermission(this)) {
            repository.updateLiveTelemetry {
                it.copy(
                    isServiceRunning = false,
                    hasPhonePermission = false,
                    currentGeneration = NetworkGeneration.UNKNOWN
                )
            }
            serviceScope.launch { repository.setMonitoringEnabled(false) }
            stopSelf()
            return START_NOT_STICKY
        }

        promoteToForegroundWithStaticNotification()
        registerSystemReceiverIfNeeded()
        rebindTelephonyCallbackIfPermitted()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterTelephonyCallback()
        unregisterSystemReceiver()
        stateMachine.reset()
        repository.updateLiveTelemetry {
            it.copy(
                isServiceRunning = false,
                pendingTargetGeneration = null
            )
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun stopMonitoringAndSelf() {
        serviceScope.launch {
            repository.setMonitoringEnabled(false)
        }
        unregisterTelephonyCallback()
        unregisterSystemReceiver()
        stateMachine.reset()
        repository.updateLiveTelemetry {
            it.copy(
                isServiceRunning = false,
                pendingTargetGeneration = null
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun promoteToForegroundWithStaticNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existingChannel = nm.getNotificationChannel(NOTIFICATION_CHANNEL_ID)
        if (existingChannel == null) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.fgs_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.fgs_channel_desc)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(channel)
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            100,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, NetworkMonitorService::class.java).apply {
            action = ACTION_STOP_MONITORING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Static notification: posted once, no per-second updates
        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.fgs_notification_title))
            .setContentText(getString(R.string.fgs_notification_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.fgs_action_stop),
                stopPendingIntent
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun registerSystemReceiverIfNeeded() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(ACTION_DEFAULT_DATA_SUB_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        ContextCompat.registerReceiver(
            this,
            systemStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isReceiverRegistered = true
    }

    private fun unregisterSystemReceiver() {
        if (!isReceiverRegistered) return
        runCatching { unregisterReceiver(systemStateReceiver) }
        isReceiverRegistered = false
    }

    @SuppressLint("MissingPermission")
    private fun rebindTelephonyCallbackIfPermitted() {
        unregisterTelephonyCallback()

        val hasPhonePerm = SystemDiagnosticsHelper.hasPhoneStatePermission(this)
        val hasNotifPerm = SystemDiagnosticsHelper.hasNotificationPermission(this)
        val airplaneOn = SystemDiagnosticsHelper.isAirplaneModeOn(this)
        val dndActive = SystemDiagnosticsHelper.isDoNotDisturbActive(this)
        val batteryOpt = SystemDiagnosticsHelper.isBatteryOptimizationActive(this)
        val (defaultSubId, simLabel) = SystemDiagnosticsHelper.resolveDefaultDataSubscriptionInfo(this)
        val hasSim = SystemDiagnosticsHelper.hasActiveSim(this)

        repository.updateLiveTelemetry {
            it.copy(
                isServiceRunning = hasPhonePerm,
                hasPhonePermission = hasPhonePerm,
                hasNotificationPermission = hasNotifPerm,
                isAirplaneModeOn = airplaneOn,
                hasActiveSim = hasSim,
                activeSubId = defaultSubId,
                activeSimCarrierName = simLabel,
                isDoNotDisturbActive = dndActive,
                isBatteryOptimizationActive = batteryOpt
            )
        }

        if (!hasPhonePerm) {
            stateMachine.onGenerationReported(NetworkGeneration.UNKNOWN)
            return
        }

        val rootTm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        val targetTm = if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            rootTm.createForSubscriptionId(defaultSubId)
        } else {
            rootTm
        }

        val callback = CellularCallback()
        try {
            targetTm.registerTelephonyCallback(mainExecutor, callback)
            boundTelephonyManager = targetTm
            registeredCallback = callback
        } catch (_: SecurityException) {
            stateMachine.onGenerationReported(NetworkGeneration.UNKNOWN)
        }
    }

    private fun unregisterTelephonyCallback() {
        val tm = boundTelephonyManager
        val cb = registeredCallback
        if (tm != null && cb != null) {
            runCatching { tm.unregisterTelephonyCallback(cb) }
        }
        boundTelephonyManager = null
        registeredCallback = null
    }

    @SuppressLint("MissingPermission")
    private fun reevaluateCurrentGeneration() {
        val hasPhonePerm = SystemDiagnosticsHelper.hasPhoneStatePermission(this)
        val airplaneOn = SystemDiagnosticsHelper.isAirplaneModeOn(this)
        val hasSim = SystemDiagnosticsHelper.hasActiveSim(this)
        val inService = lastServiceState == ServiceState.STATE_IN_SERVICE
        val dndActive = SystemDiagnosticsHelper.isDoNotDisturbActive(this)
        val batteryOpt = SystemDiagnosticsHelper.isBatteryOptimizationActive(this)
        val (defaultSubId, simLabel) = SystemDiagnosticsHelper.resolveDefaultDataSubscriptionInfo(this)

        val displayInfo = lastDisplayInfo
        val baseNetworkType = displayInfo?.networkType
            ?: runCatching { boundTelephonyManager?.dataNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN }
                .getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)
        val overrideType = displayInfo?.overrideNetworkType
            ?: TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE

        val mappedGeneration = NetworkGenerationMapper.map(
            networkType = baseNetworkType,
            overrideNetworkType = overrideType,
            hasPermission = hasPhonePerm,
            isAirplaneModeOn = airplaneOn,
            hasActiveSim = hasSim,
            isInService = inService
        )

        repository.updateLiveTelemetry {
            it.copy(
                hasPhonePermission = hasPhonePerm,
                isAirplaneModeOn = airplaneOn,
                hasActiveSim = hasSim,
                isInService = inService,
                activeSubId = defaultSubId,
                activeSimCarrierName = simLabel,
                isDoNotDisturbActive = dndActive,
                isBatteryOptimizationActive = batteryOpt
            )
        }

        stateMachine.onGenerationReported(mappedGeneration)
    }

    private fun handleConfirmedTransition(event: TransitionEvent) {
        serviceScope.launch {
            repository.recordTransitionEvent(event)
            // Monitoring ON always implies sound alerts; there is no separate master sound switch.
            val settings = latestSettings.copy(masterSoundEnabled = true)
            val toneToPlay = settings.resolveToneFor(event.fromGeneration, event.toGeneration)
            val rule = settings.ruleFor(event.fromGeneration, event.toGeneration)
            val shouldHaptic = settings.hapticFeedbackEnabled && (rule?.enabled == true)
            audioPlayer.playAlert(
                tone = toneToPlay,
                volumePercent = settings.alertVolumePercent,
                customSoundFileName = settings.customSoundFileName,
                triggerHaptics = shouldHaptic
            )
        }
    }

    /**
     * Implements [TelephonyCallback.DisplayInfoListener], [TelephonyCallback.SignalStrengthsListener],
     * and [TelephonyCallback.ServiceStateListener] for accurate, zero-polling cellular telemetry.
     */
    private inner class CellularCallback :
        TelephonyCallback(),
        TelephonyCallback.DisplayInfoListener,
        TelephonyCallback.SignalStrengthsListener,
        TelephonyCallback.ServiceStateListener {

        override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
            lastDisplayInfo = telephonyDisplayInfo
            reevaluateCurrentGeneration()
        }

        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            val level = signalStrength.level.coerceIn(0, 4)
            stateMachine.updateSignalBars(level)
            repository.updateLiveTelemetry {
                it.copy(signalLevel = SignalLevel.fromLevel(level))
            }
        }

        override fun onServiceStateChanged(serviceState: ServiceState) {
            lastServiceState = serviceState.state
            reevaluateCurrentGeneration()
        }
    }
}
