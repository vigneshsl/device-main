package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.PendingPermissionRationale
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MagicalMintGlow
import com.example.ui.theme.SmartNetworkAlertTheme

enum class AppDestination {
    HOME,
    SETTINGS,
    DIAGNOSTICS
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            SmartNetworkAlertTheme(
                dynamicColor = uiState.settings.dynamicColorEnabled
            ) {
                SmartNetworkAlertApp(
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun SmartNetworkAlertApp(
    uiState: MainUiState,
    viewModel: MainViewModel
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh permissions & radio snapshot whenever returning to foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSystemDiagnosticsSnapshot()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Handle system Back navigation on secondary tabs
    if (currentDestination != AppDestination.HOME) {
        BackHandler {
            currentDestination = AppDestination.HOME
        }
    }

    // Runtime permission launchers
    val phonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPhonePermissionResult(granted)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onNotificationPermissionResult(granted)
    }

    // Custom audio file picker (OpenDocument for audio/*)
    val customAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importCustomSound(uri)
        }
    }

    // Display snackbar status messages
    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    // Rationale Dialog BEFORE each runtime permission request
    when (uiState.pendingRationale) {
        PendingPermissionRationale.READ_PHONE_STATE -> {
            PermissionRationaleDialog(
                title = stringResource(R.string.rationale_phone_title),
                body = stringResource(R.string.rationale_phone_body),
                icon = Icons.Filled.Security,
                onConfirm = {
                    viewModel.dismissRationale()
                    phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                },
                onDismiss = { viewModel.dismissRationale() },
                testTag = "rationale_dialog_phone"
            )
        }
        PendingPermissionRationale.POST_NOTIFICATIONS -> {
            PermissionRationaleDialog(
                title = stringResource(R.string.rationale_notification_title),
                body = stringResource(R.string.rationale_notification_body),
                icon = Icons.Filled.NotificationsActive,
                onConfirm = {
                    viewModel.dismissRationale()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.onNotificationPermissionResult(true)
                    }
                },
                onDismiss = {
                    viewModel.onNotificationPermissionResult(false)
                },
                testTag = "rationale_dialog_notification"
            )
        }
        PendingPermissionRationale.NONE -> Unit
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.testTag("main_snackbar_host")
            )
        },
        bottomBar = {
            FloatingMagicalBottomBar(
                currentDestination = currentDestination,
                onSelectDestination = { currentDestination = it }
            )
        }
    ) { innerPadding ->
        Crossfade(
            targetState = currentDestination,
            animationSpec = tween(durationMillis = if (uiState.removeAnimationsEnabled) 0 else 180),
            label = "main_tab_crossfade",
            modifier = Modifier.padding(innerPadding)
        ) { destination ->
            when (destination) {
                AppDestination.HOME -> {
                    HomeScreen(
                        uiState = uiState,
                        onToggleMonitoring = { viewModel.onToggleMonitoringClicked(it) },
                        onRequestPhonePermission = { viewModel.requestPhonePermissionRationale() },
                        onRequestNotificationPermission = { viewModel.requestNotificationPermissionRationale() },
                        onNavigateToSettings = { currentDestination = AppDestination.SETTINGS },
                        onNavigateToDiagnostics = { currentDestination = AppDestination.DIAGNOSTICS },
                        onClearHistory = { viewModel.clearHistory() }
                    )
                }
                AppDestination.SETTINGS -> {
                    SettingsScreen(
                        settings = uiState.settings,
                        onMasterSoundChanged = { viewModel.setMasterSoundEnabled(it) },
                        onHapticChanged = { viewModel.setHapticFeedbackEnabled(it) },
                        onVolumeChanged = { viewModel.setAlertVolumePercent(it) },
                        onDebounceChanged = { viewModel.setDebounceMillis(it) },
                        onRecoverySoundChanged = { viewModel.setUseRecoverySoundOnUpgrade(it) },
                        onDynamicColorChanged = { viewModel.setDynamicColorEnabled(it) },
                        onPickCustomSound = {
                            customAudioLauncher.launch(
                                arrayOf("audio/*", "application/ogg")
                            )
                        },
                        onRemoveCustomSound = { viewModel.removeCustomSound() },
                        onUpdateTransitionRule = { key, enabled, tone ->
                            viewModel.updateTransitionRule(key, enabled, tone)
                        },
                        onTestTransitionTone = { key -> viewModel.testTransitionRuleTone(key) },
                        onPreviewDefaultSound = { viewModel.previewActiveAlertSound() },
                        onBackClick = { currentDestination = AppDestination.HOME }
                    )
                }
                AppDestination.DIAGNOSTICS -> {
                    DiagnosticsScreen(
                        telemetry = uiState.telemetry,
                        onRefreshDiagnostics = { viewModel.refreshSystemDiagnosticsSnapshot() },
                        onBackClick = { currentDestination = AppDestination.HOME },
                        onRequestPhonePermission = { viewModel.requestPhonePermissionRationale() },
                        onRequestNotificationPermission = { viewModel.requestNotificationPermissionRationale() }
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingMagicalBottomBar(
    currentDestination: AppDestination,
    onSelectDestination: (AppDestination) -> Unit
) {
    val isDark = isSystemInDarkTheme()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = if (isDark) Color(0xFF122119).copy(alpha = 0.96f) else Color.White.copy(alpha = 0.96f),
            shadowElevation = 10.dp,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(30.dp)
                )
                .testTag("bottom_nav_bar")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MagicalBottomNavTab(
                    selected = currentDestination == AppDestination.HOME,
                    onClick = { onSelectDestination(AppDestination.HOME) },
                    selectedIcon = Icons.Filled.Home,
                    unselectedIcon = Icons.Outlined.Home,
                    tamilLabel = stringResource(R.string.nav_home_ta),
                    englishLabel = stringResource(R.string.nav_home),
                    testTag = "nav_tab_home",
                    modifier = Modifier.weight(1f)
                )

                MagicalBottomNavTab(
                    selected = currentDestination == AppDestination.SETTINGS,
                    onClick = { onSelectDestination(AppDestination.SETTINGS) },
                    selectedIcon = Icons.Filled.MusicNote,
                    unselectedIcon = Icons.Outlined.MusicNote,
                    tamilLabel = stringResource(R.string.nav_settings_ta),
                    englishLabel = stringResource(R.string.nav_settings),
                    testTag = "nav_tab_settings",
                    modifier = Modifier.weight(1f)
                )

                MagicalBottomNavTab(
                    selected = currentDestination == AppDestination.DIAGNOSTICS,
                    onClick = { onSelectDestination(AppDestination.DIAGNOSTICS) },
                    selectedIcon = Icons.Filled.SignalCellularAlt,
                    unselectedIcon = Icons.Outlined.SignalCellularAlt,
                    tamilLabel = stringResource(R.string.nav_diagnostics_ta),
                    englishLabel = stringResource(R.string.nav_diagnostics),
                    testTag = "nav_tab_diagnostics",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Subtle Developer Branding Footer matching reference image
        Text(
            text = "</> ${stringResource(R.string.dev_brand_footer)}",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("developer_branding_footer")
        )
    }
}

@Composable
private fun MagicalBottomNavTab(
    selected: Boolean,
    onClick: () -> Unit,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    tamilLabel: String,
    englishLabel: String,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val pillShape = RoundedCornerShape(24.dp)

    val bgBrush = if (selected) {
        Brush.verticalGradient(
            colors = if (isDark) {
                listOf(Color(0xFF1E462D), Color(0xFF153320))
            } else {
                listOf(Color(0xFFD8F999).copy(alpha = 0.75f), Color(0xFFBBF7D0).copy(alpha = 0.90f))
            }
        )
    } else {
        Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
    }

    val contentTint = if (selected) {
        if (isDark) Color(0xFF86EFAC) else Color(0xFF064E3B)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .padding(horizontal = 3.dp)
            .then(
                if (selected) {
                    Modifier.shadow(
                        elevation = 6.dp,
                        shape = pillShape,
                        ambientColor = MagicalMintGlow,
                        spotColor = MagicalMintGlow
                    )
                } else {
                    Modifier
                }
            )
            .clip(pillShape)
            .background(bgBrush)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .padding(vertical = 6.dp, horizontal = 6.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (selected) selectedIcon else unselectedIcon,
                contentDescription = englishLabel,
                tint = contentTint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tamilLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = contentTint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = englishLabel,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.Medium,
                color = contentTint.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PermissionRationaleDialog(
    title: String,
    body: String,
    icon: ImageVector,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    testTag: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(imageVector = icon, contentDescription = null) },
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text = body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("${testTag}_confirm")
            ) {
                Text(stringResource(R.string.rationale_grant_button))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("${testTag}_dismiss")
            ) {
                Text(stringResource(R.string.rationale_cancel_button))
            }
        },
        modifier = Modifier.testTag(testTag)
    )
}
