package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.LiveRadioTelemetry
import com.example.domain.NetworkGeneration
import com.example.ui.theme.BilingualText
import com.example.ui.theme.MagicalGlassCard
import com.example.ui.theme.MagicalIconCircle
import com.example.ui.theme.MagicalScreenTopBar
import com.example.ui.theme.MagicalStatusPill
import com.example.ui.theme.MagicalTipBg
import com.example.ui.theme.MagicalTipIcon
import com.example.ui.theme.MagicalWaveBackdrop

@Composable
fun DiagnosticsScreen(
    telemetry: LiveRadioTelemetry,
    onRefreshDiagnostics: () -> Unit,
    onBackClick: () -> Unit = {},
    onRequestPhonePermission: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    var showAdvancedDetails by rememberSaveable { mutableStateOf(false) }

    val activeBars = if (telemetry.currentGeneration == NetworkGeneration.UNKNOWN) {
        0
    } else {
        telemetry.signalLevel.bars
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        MagicalWaveBackdrop(modifier = Modifier.fillMaxSize())

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .testTag("diagnostics_lazy_column"),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Bar: < Back + சாதன நிலை / Diagnostics
            item {
                MagicalScreenTopBar(
                    tamilTitle = stringResource(R.string.diagnostics_screen_title_ta),
                    englishTitle = stringResource(R.string.diagnostics_screen_title),
                    onBackClick = onBackClick
                )
            }

            // 1. Current Network Row Card
            item {
                DiagnosticMetricCard(
                    icon = Icons.Filled.CellTower,
                    tamilTitle = stringResource(R.string.diag_network_title_ta),
                    englishTitle = stringResource(R.string.diag_network_title),
                    valueText = telemetry.currentGeneration.displayName,
                    testTag = "diag_card_network"
                )
            }

            // 2. Signal Strength Row Card
            item {
                DiagnosticMetricCard(
                    icon = Icons.Filled.SignalCellularAlt,
                    tamilTitle = stringResource(R.string.diag_signal_title_ta),
                    englishTitle = stringResource(R.string.diag_signal_title),
                    valueText = "$activeBars / 4",
                    testTag = "diag_card_signal"
                )
            }

            // 3. Data SIM Row Card
            item {
                DiagnosticMetricCard(
                    icon = Icons.Filled.SimCard,
                    tamilTitle = stringResource(R.string.diag_sim_title_ta),
                    englishTitle = stringResource(R.string.diag_sim_title),
                    valueText = if (!telemetry.hasActiveSim) {
                        stringResource(R.string.sim_no_sim_ta)
                    } else {
                        telemetry.activeSimCarrierName
                    },
                    testTag = "diag_card_sim"
                )
            }

            // 4. Service Status Row Card
            item {
                DiagnosticPillCard(
                    icon = Icons.Filled.Settings,
                    tamilTitle = stringResource(R.string.diag_service_title_ta),
                    englishTitle = stringResource(R.string.diag_service_title),
                    pillText = if (telemetry.isServiceRunning) {
                        stringResource(R.string.status_running)
                    } else {
                        stringResource(R.string.status_stopped)
                    },
                    isPositive = telemetry.isServiceRunning,
                    testTag = "diag_card_service"
                )
            }

            // 5. Battery Optimization Row Card
            item {
                DiagnosticPillCard(
                    icon = Icons.Filled.BatteryChargingFull,
                    tamilTitle = stringResource(R.string.diag_battery_title_ta),
                    englishTitle = stringResource(R.string.diag_battery_title),
                    pillText = if (telemetry.isBatteryOptimizationActive) {
                        stringResource(R.string.status_normal)
                    } else {
                        stringResource(R.string.status_unrestricted)
                    },
                    isPositive = true,
                    onClick = {
                        runCatching {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                    },
                    testTag = "doze_explainer_card"
                )
            }

            // Guidance cards appear only when a real problem is detected
            if (!telemetry.hasPhonePermission) {
                item {
                    GuidanceCard(
                        tamilTitle = stringResource(R.string.guide_phone_title_ta),
                        englishTitle = stringResource(R.string.guide_phone_title),
                        tamilBody = stringResource(R.string.guide_phone_body_ta),
                        englishBody = stringResource(R.string.guide_phone_body),
                        onAction = onRequestPhonePermission,
                        testTag = "banner_no_permission"
                    )
                }
            } else if (!telemetry.hasNotificationPermission) {
                item {
                    GuidanceCard(
                        tamilTitle = stringResource(R.string.guide_notif_title_ta),
                        englishTitle = stringResource(R.string.guide_notif_title),
                        tamilBody = stringResource(R.string.guide_notif_body_ta),
                        englishBody = stringResource(R.string.guide_notif_body),
                        onAction = onRequestNotificationPermission,
                        testTag = "banner_no_notification_permission"
                    )
                }
            }
            if (telemetry.isAirplaneModeOn) {
                item {
                    GuidanceCard(
                        tamilTitle = stringResource(R.string.guide_airplane_title_ta),
                        englishTitle = stringResource(R.string.guide_airplane_title),
                        tamilBody = stringResource(R.string.guide_no_service_body_ta),
                        englishBody = stringResource(R.string.guide_no_service_body),
                        testTag = "banner_airplane_mode"
                    )
                }
            } else if (telemetry.hasPhonePermission && (!telemetry.hasActiveSim || !telemetry.isInService)) {
                item {
                    GuidanceCard(
                        tamilTitle = stringResource(R.string.guide_no_service_title_ta),
                        englishTitle = stringResource(R.string.guide_no_service_title),
                        tamilBody = stringResource(R.string.guide_no_service_body_ta),
                        englishBody = stringResource(R.string.guide_no_service_body),
                        testTag = "banner_no_service"
                    )
                }
            }
            if (telemetry.isDoNotDisturbActive) {
                item {
                    GuidanceCard(
                        tamilTitle = stringResource(R.string.guide_dnd_title_ta),
                        englishTitle = stringResource(R.string.guide_dnd_title),
                        tamilBody = stringResource(R.string.guide_dnd_body_ta),
                        englishBody = stringResource(R.string.guide_dnd_body),
                        testTag = "banner_dnd_active"
                    )
                }
            }

            // 6. Tip Card (குறிப்பு / Tip)
            item {
                MagicalGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("diag_tip_card"),
                    cornerRadius = 22.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        MagicalIconCircle(
                            icon = Icons.Filled.Lightbulb,
                            size = 44.dp,
                            iconSize = 24.dp,
                            circleColor = if (isDark) Color(0xFF3B3010) else MagicalTipBg,
                            iconTint = MagicalTipIcon
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            BilingualText(
                                tamil = stringResource(R.string.diag_tip_title_ta),
                                english = stringResource(R.string.diag_tip_title),
                                tamilStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                englishStyle = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.diag_tip_body_ta),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.diag_tip_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 7. Expandable Permissions, DND, Battery & Developer Credits Section
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TextButton(
                        onClick = { showAdvancedDetails = !showAdvancedDetails },
                        modifier = Modifier.testTag("toggle_diagnostics_details_button")
                    ) {
                        Icon(
                            imageVector = if (showAdvancedDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(
                                if (showAdvancedDetails) R.string.details_hide else R.string.details_show
                            )
                        )
                    }

                    if (showAdvancedDetails) {
                        Spacer(modifier = Modifier.height(6.dp))
                        MagicalGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("system_checklist_card"),
                            cornerRadius = 22.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BilingualText(
                                        tamil = stringResource(R.string.permissions_title_ta),
                                        english = stringResource(R.string.permissions_title),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedButton(
                                        onClick = onRefreshDiagnostics,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("refresh_diagnostics_button")
                                    ) {
                                        Text(stringResource(R.string.refresh_button))
                                    }
                                }

                                DiagnosticStatusRow(
                                    label = stringResource(R.string.perm_phone_label),
                                    status = stringResource(
                                        if (telemetry.hasPhonePermission) R.string.status_granted else R.string.status_required
                                    ),
                                    isHealthy = telemetry.hasPhonePermission
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                DiagnosticStatusRow(
                                    label = stringResource(R.string.perm_notif_label),
                                    status = stringResource(
                                        if (telemetry.hasNotificationPermission) R.string.status_granted else R.string.status_recommended
                                    ),
                                    isHealthy = telemetry.hasNotificationPermission
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                DiagnosticStatusRow(
                                    label = stringResource(R.string.dnd_label),
                                    status = stringResource(
                                        if (telemetry.isDoNotDisturbActive) R.string.status_dnd_on else R.string.status_dnd_off
                                    ),
                                    isHealthy = !telemetry.isDoNotDisturbActive
                                )

                                FilledTonalButton(
                                    onClick = {
                                        runCatching {
                                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(intent)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("open_battery_settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.battery_open_settings_btn))
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                                // Developer Credits in About / Details section
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = stringResource(R.string.about_title),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(R.string.dev_brand_short),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = stringResource(R.string.dev_brand_powered),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(R.string.dev_brand_crafted),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun GuidanceCard(
    tamilTitle: String,
    englishTitle: String,
    tamilBody: String,
    englishBody: String,
    testTag: String,
    onAction: (() -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    MagicalGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        cornerRadius = 22.dp,
        borderColor = Color(0xFFF59E0B).copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            MagicalIconCircle(
                icon = Icons.Filled.WarningAmber,
                size = 44.dp,
                iconSize = 22.dp,
                circleColor = if (isDark) Color(0xFF3B3010) else MagicalTipBg,
                iconTint = Color(0xFFD97706)
            )
            Column(modifier = Modifier.weight(1f)) {
                BilingualText(
                    tamil = tamilTitle,
                    english = englishTitle,
                    tamilStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = tamilBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = englishBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onAction != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = onAction,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("${stringResource(R.string.grant_permission_cta_ta)} / ${stringResource(R.string.grant_permission_cta)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticMetricCard(
    icon: ImageVector,
    tamilTitle: String,
    englishTitle: String,
    valueText: String,
    testTag: String
) {
    MagicalGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        cornerRadius = 22.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                MagicalIconCircle(
                    icon = icon,
                    size = 44.dp,
                    iconSize = 22.dp
                )
                BilingualText(
                    tamil = tamilTitle,
                    english = englishTitle,
                    tamilStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                    englishStyle = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                text = valueText,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DiagnosticPillCard(
    icon: ImageVector,
    tamilTitle: String,
    englishTitle: String,
    pillText: String,
    isPositive: Boolean,
    testTag: String,
    onClick: (() -> Unit)? = null
) {
    MagicalGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        cornerRadius = 22.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                MagicalIconCircle(
                    icon = icon,
                    size = 44.dp,
                    iconSize = 22.dp
                )
                BilingualText(
                    tamil = tamilTitle,
                    english = englishTitle,
                    tamilStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                    englishStyle = MaterialTheme.typography.bodySmall
                )
            }

            MagicalStatusPill(
                text = pillText,
                isPositive = isPositive
            )
        }
    }
}

@Composable
private fun DiagnosticStatusRow(
    label: String,
    status: String,
    isHealthy: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = if (isHealthy) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                fontWeight = FontWeight.SemiBold
            )
        }
        Icon(
            imageVector = if (isHealthy) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
            contentDescription = if (isHealthy) "Healthy" else "Attention needed",
            tint = if (isHealthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}
