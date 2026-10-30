package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.NetworkGeneration
import com.example.domain.SignalLevel
import com.example.domain.TransitionEvent
import com.example.ui.MainUiState
import com.example.ui.MonitoringSwitchState
import com.example.ui.theme.BilingualText
import com.example.ui.theme.Gen2GAccentDark
import com.example.ui.theme.Gen2GAccentLight
import com.example.ui.theme.Gen3GAccentDark
import com.example.ui.theme.Gen3GAccentLight
import com.example.ui.theme.Gen4GAccentDark
import com.example.ui.theme.Gen4GAccentLight
import com.example.ui.theme.Gen5GAccentDark
import com.example.ui.theme.Gen5GAccentLight
import com.example.ui.theme.GenUnknownAccentDark
import com.example.ui.theme.GenUnknownAccentLight
import com.example.ui.theme.MagicalGlassCard
import com.example.ui.theme.MagicalIconCircle
import com.example.ui.theme.MagicalLimeGlow
import com.example.ui.theme.MagicalMintGlow
import com.example.ui.theme.MagicalSwitchTrackOn
import com.example.ui.theme.MagicalWaveBackdrop
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    uiState: MainUiState,
    onToggleMonitoring: (Boolean) -> Unit,
    onRequestPhonePermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToDiagnostics: () -> Unit = {},
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetry = uiState.telemetry
    val isDark = isSystemInDarkTheme()
    var historyExpanded by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        // Subtle organic mint-green wave and particle background matching reference
        MagicalWaveBackdrop(modifier = Modifier.fillMaxSize())

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .testTag("home_lazy_column"),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Top Header with glowing green antenna badge, Tamil-first title, and Settings icon
            item {
                MonitorHeaderSection(onNavigateToSettings = onNavigateToSettings)
            }

            // 2. Centerpiece Glowing Green Network Orb
            item {
                GenerationHeroOrbSection(
                    generation = telemetry.currentGeneration,
                    pendingTarget = telemetry.pendingTargetGeneration,
                    debounceMillis = uiState.settings.debounceMillis,
                    removeAnimations = uiState.removeAnimationsEnabled,
                    isDark = isDark
                )
            }

            // 3. Side-by-Side Signal Strength & Data SIM Cards
            item {
                SignalAndSimRow(
                    signalLevel = telemetry.signalLevel,
                    generation = telemetry.currentGeneration,
                    carrierName = telemetry.activeSimCarrierName,
                    hasActiveSim = telemetry.hasActiveSim,
                    removeAnimations = uiState.removeAnimationsEnabled
                )
            }

            // 4. Master Network Monitoring ON/OFF Card (auto-enables sound alerts)
            item {
                MonitoringControlCard(
                    switchState = uiState.monitoringSwitchState,
                    isSwitchChecked = uiState.settings.monitoringEnabled && telemetry.hasPhonePermission,
                    onToggle = onToggleMonitoring,
                    onRequestPermission = onRequestPhonePermission,
                    hasNotificationPermission = telemetry.hasNotificationPermission,
                    onRequestNotificationPermission = onRequestNotificationPermission
                )
            }

            // 5. Latest Network Change Card (expandable for full history)
            item {
                LastChangeBannerCard(
                    lastTransition = uiState.lastTransition,
                    historyExpanded = historyExpanded,
                    onToggleExpand = { historyExpanded = !historyExpanded }
                )
            }

            // Expandable Transition History List
            if (historyExpanded) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BilingualText(
                            tamil = stringResource(R.string.history_section_title_ta),
                            english = stringResource(R.string.history_section_title),
                            tamilStyle = MaterialTheme.typography.titleMedium,
                            englishStyle = MaterialTheme.typography.bodySmall
                        )

                        if (uiState.history.isNotEmpty()) {
                            OutlinedButton(
                                onClick = onClearHistory,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("clear_history_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = stringResource(R.string.history_clear_button_ta))
                            }
                        }
                    }
                }

                if (uiState.history.isEmpty()) {
                    item {
                        EmptyHistoryCard()
                    }
                } else {
                    items(
                        items = uiState.history,
                        key = { it.id }
                    ) { event ->
                        TransitionHistoryItemCard(event = event, isDark = isDark)
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
private fun MonitorHeaderSection(
    onNavigateToSettings: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Glowing green circular antenna icon at top center
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = CircleShape,
                        ambientColor = MagicalMintGlow,
                        spotColor = MagicalMintGlow
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = if (isDark) {
                                listOf(Color(0xFF1E3F2B), Color(0xFF12261A))
                            } else {
                                listOf(Color(0xFFE6FAEC), Color(0xFFC6F6D5))
                            }
                        )
                    )
                    .border(1.5.dp, MagicalMintGlow.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CellTower,
                    contentDescription = null,
                    tint = if (isDark) Color(0xFF4ADE80) else Color(0xFF15803D),
                    modifier = Modifier.size(28.dp)
                )
            }

            // Top-right Settings gear button matching reference image
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                shadowElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(40.dp)
            ) {
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.testTag("header_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.nav_settings),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tamil Heading First, English immediately below
        Text(
            text = stringResource(R.string.app_title_ta),
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 17.sp,
                lineHeight = 23.sp
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.app_name_en),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 3.dp)
                .clip(RoundedCornerShape(50))
                .background(MagicalMintGlow)
        )
    }
}

@Composable
private fun GenerationHeroOrbSection(
    generation: NetworkGeneration,
    pendingTarget: NetworkGeneration?,
    debounceMillis: Long,
    removeAnimations: Boolean,
    isDark: Boolean
) {
    val accentColor = generationAccentColor(generation, isDark)
    val liveDesc = stringResource(R.string.hero_cd_format, generation.displayName)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = liveDesc
            }
            .testTag("generation_hero_card"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Magical glowing circular orb centerpiece
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(216.dp)
        ) {
            val rankFraction = when (generation) {
                NetworkGeneration.FIVE_G -> 1.0f
                NetworkGeneration.FOUR_G -> 0.80f
                NetworkGeneration.THREE_G -> 0.56f
                NetworkGeneration.TWO_G -> 0.32f
                NetworkGeneration.UNKNOWN -> 0.15f
            }
            val animatedSweep by animateFloatAsState(
                targetValue = rankFraction,
                animationSpec = if (removeAnimations) {
                    tween(durationMillis = 0)
                } else {
                    spring(stiffness = Spring.StiffnessLow)
                },
                label = "generation_arc_sweep"
            )

            // Outer soft radial green aura
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerOffset = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MagicalMintGlow.copy(alpha = if (isDark) 0.28f else 0.42f),
                            MagicalLimeGlow.copy(alpha = if (isDark) 0.12f else 0.18f),
                            Color.Transparent
                        ),
                        center = centerOffset,
                        radius = size.minDimension / 2f
                    ),
                    radius = size.minDimension / 2f,
                    center = centerOffset
                )
            }

            // Inner white/glass circular orb with glowing green border ring
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(168.dp)
                    .shadow(
                        elevation = 14.dp,
                        shape = CircleShape,
                        ambientColor = MagicalMintGlow,
                        spotColor = MagicalMintGlow
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = if (isDark) {
                                listOf(Color(0xFF183123), Color(0xFF0F2117))
                            } else {
                                listOf(Color(0xFFFFFFFF), Color(0xFFF0FDF4))
                            }
                        )
                    )
                    .border(
                        width = 2.dp,
                        color = Color.White.copy(alpha = if (isDark) 0.15f else 0.85f),
                        shape = CircleShape
                    )
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                ) {
                    val strokeWidth = 8.dp.toPx()
                    drawCircle(
                        color = if (isDark) Color(0xFF234230) else Color(0xFFD1FAE5),
                        style = Stroke(width = strokeWidth)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFF22C55E),
                                Color(0xFF86EFAC),
                                Color(0xFF4ADE80),
                                Color(0xFF16A34A)
                            )
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * animatedSweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                if (removeAnimations) {
                    GenerationCenterLabel(
                        generation = generation,
                        accentColor = accentColor,
                        isDark = isDark
                    )
                } else {
                    AnimatedContent(
                        targetState = generation,
                        transitionSpec = {
                            fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                        },
                        label = "generation_badge_transition"
                    ) { targetGen ->
                        GenerationCenterLabel(
                            generation = targetGen,
                            accentColor = generationAccentColor(targetGen, isDark),
                            isDark = isDark
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Tamil-first label below the orb: தற்போதைய நெட்வொர்க் / Current Network
        BilingualText(
            tamil = stringResource(R.string.generation_label_current_ta),
            english = stringResource(R.string.generation_label_current),
            tamilStyle = MaterialTheme.typography.titleMedium,
            englishStyle = MaterialTheme.typography.bodySmall,
            horizontalAlignment = Alignment.CenterHorizontally,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("generation_technical_summary")
        )

        if (pendingTarget != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = if (isDark) Color(0xFF1A3828) else Color(0xFFDCFCE7),
                contentColor = if (isDark) Color(0xFF86EFAC) else Color(0xFF15803D),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = stringResource(R.string.pending_change_format, pendingTarget.displayName, debounceMillis / 1000f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun GenerationCenterLabel(
    generation: NetworkGeneration,
    accentColor: Color,
    isDark: Boolean
) {
    val mainTextColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F291E)
    val pillBg = if (isDark) Color(0xFF1E3F2B) else Color(0xFFDCFCE7)
    val pillFg = if (isDark) Color(0xFF86EFAC) else Color(0xFF15803D)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = generation.displayName,
            style = if (generation == NetworkGeneration.UNKNOWN) {
                MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp)
            } else {
                MaterialTheme.typography.displayLarge.copy(fontSize = 54.sp)
            },
            color = if (generation == NetworkGeneration.UNKNOWN) accentColor else mainTextColor,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.testTag("current_generation_text")
        )

        if (generation == NetworkGeneration.UNKNOWN) {
            Text(
                text = stringResource(R.string.generation_unknown_ta),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Surface(
            color = pillBg,
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, pillFg.copy(alpha = 0.25f))
        ) {
            Text(
                text = generation.orbBadge,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = pillFg,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun SignalAndSimRow(
    signalLevel: SignalLevel,
    generation: NetworkGeneration,
    carrierName: String,
    hasActiveSim: Boolean,
    removeAnimations: Boolean
) {
    val activeBars = if (generation == NetworkGeneration.UNKNOWN) 0 else signalLevel.bars
    val signalDesc = stringResource(R.string.signal_cd_format, activeBars)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left Card: Signal Strength
        MagicalGlassCard(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {
                    contentDescription = signalDesc
                }
                .testTag("signal_strength_card"),
            cornerRadius = 22.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SignalBarsMeter(
                    bars = activeBars,
                    removeAnimations = removeAnimations,
                    modifier = Modifier
                        .size(width = 30.dp, height = 28.dp)
                        .testTag("signal_bars_canvas")
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.signal_strength_title_ta),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.signal_strength_title),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$activeBars / 4",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF15803D),
                        modifier = Modifier.testTag("signal_level_text")
                    )
                }
            }
        }

        // Right Card: Default Data SIM
        MagicalGlassCard(
            modifier = Modifier
                .weight(1f)
                .testTag("dual_sim_banner"),
            cornerRadius = 22.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MagicalIconCircle(
                    icon = Icons.Filled.SimCard,
                    size = 38.dp,
                    iconSize = 20.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sim_data_title_ta),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.sim_data_title),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (!hasActiveSim) {
                            stringResource(R.string.sim_no_sim_ta)
                        } else {
                            carrierName
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalBarsMeter(
    bars: Int,
    removeAnimations: Boolean,
    modifier: Modifier = Modifier
) {
    val filledColor = Color(0xFF16A34A)
    val emptyColor = Color(0xFFDCFCE7)

    val animatedBars by animateFloatAsState(
        targetValue = bars.toFloat(),
        animationSpec = if (removeAnimations) tween(0) else tween(200),
        label = "signal_bars_anim"
    )

    Canvas(modifier = modifier) {
        val totalBars = 4
        val spacing = 3.5.dp.toPx()
        val barWidth = (size.width - spacing * (totalBars - 1)) / totalBars
        for (i in 0 until totalBars) {
            val barNumber = i + 1
            val heightFraction = 0.32f + (i * 0.22f)
            val barHeight = size.height * heightFraction
            val left = i * (barWidth + spacing)
            val top = size.height - barHeight
            val isActive = animatedBars >= (barNumber - 0.2f)

            drawRoundRect(
                color = if (isActive) filledColor else emptyColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
            )
        }
    }
}

@Composable
private fun MonitoringControlCard(
    switchState: MonitoringSwitchState,
    isSwitchChecked: Boolean,
    onToggle: (Boolean) -> Unit,
    onRequestPermission: () -> Unit,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val (statusTa, statusEn, dotColor) = when (switchState) {
        MonitoringSwitchState.RUNNING -> Triple(
            stringResource(R.string.monitor_status_running_ta),
            stringResource(R.string.monitor_status_running),
            Color(0xFF22C55E)
        )
        MonitoringSwitchState.STOPPED -> Triple(
            stringResource(R.string.monitor_status_stopped_ta),
            stringResource(R.string.monitor_status_stopped),
            Color(0xFF94A3B8)
        )
        MonitoringSwitchState.PERMISSION_NEEDED -> Triple(
            stringResource(R.string.monitor_status_permission_needed_ta),
            stringResource(R.string.monitor_status_permission_needed),
            Color(0xFFEF4444)
        )
    }

    MagicalGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("monitoring_control_card"),
        cornerRadius = 24.dp,
        containerColor = if (isDark) Color(0xFF14271D) else Color(0xFFF2FCF5),
        borderColor = if (switchState == MonitoringSwitchState.RUNNING) {
            MagicalMintGlow.copy(alpha = 0.9f)
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MagicalIconCircle(
                        icon = Icons.Filled.CellTower,
                        size = 44.dp,
                        iconSize = 24.dp
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.monitor_toggle_label_ta),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.monitor_toggle_label),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.monitor_toggle_sub_ta),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.monitor_toggle_sub),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Switch(
                    checked = isSwitchChecked,
                    onCheckedChange = { desired -> onToggle(desired) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MagicalSwitchTrackOn,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .semantics {
                            contentDescription = "$statusTa. $statusEn"
                        }
                        .testTag("monitoring_switch")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            Spacer(modifier = Modifier.height(10.dp))

            // Active / Stopped / Permission Status Footer with glowing green indicator dot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }

                Column(modifier = Modifier.testTag("monitoring_state_label")) {
                    Text(
                        text = statusTa,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp),
                        fontWeight = FontWeight.Bold,
                        color = if (switchState == MonitoringSwitchState.RUNNING) {
                            Color(0xFF15803D)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = statusEn,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (switchState == MonitoringSwitchState.PERMISSION_NEEDED) {
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onRequestPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("grant_phone_permission_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${stringResource(R.string.grant_permission_cta_ta)} / ${stringResource(R.string.grant_permission_cta)}")
                }
            } else if (!hasNotificationPermission && isSwitchChecked) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onRequestNotificationPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("banner_no_notification_permission")
                ) {
                    Text("${stringResource(R.string.guide_notif_title_ta)} / ${stringResource(R.string.guide_notif_title)}")
                }
            }
        }
    }
}

@Composable
private fun LastChangeBannerCard(
    lastTransition: TransitionEvent?,
    historyExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val bannerText = if (lastTransition != null) {
        stringResource(
            R.string.last_change_cd_format,
            lastTransition.fromGeneration.displayName,
            lastTransition.toGeneration.displayName
        )
    } else {
        stringResource(R.string.last_change_none)
    }
    val todayTa = stringResource(R.string.today_ta)
    val todayEn = stringResource(R.string.today_en)
    val bilingualTimestamp = remember(lastTransition?.timestampMillis, todayTa, todayEn) {
        lastTransition?.timestampMillis?.let { formatBilingualTimestamp(it, todayTa, todayEn) }
    }
    val highlightGreen = if (isSystemInDarkTheme()) Color(0xFF4ADE80) else Color(0xFF16A34A)

    MagicalGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = bannerText
            }
            .testTag("last_change_banner"),
        cornerRadius = 24.dp,
        onClick = onToggleExpand
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            MagicalIconCircle(
                icon = Icons.Filled.SwapHoriz,
                size = 44.dp,
                iconSize = 24.dp
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.last_change_title_ta),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.last_change_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (lastTransition != null) {
                    Text(
                        text = buildAnnotatedString {
                            append("${lastTransition.fromGeneration.displayName} \u2192 ")
                            withStyle(SpanStyle(color = highlightGreen)) {
                                append(lastTransition.toGeneration.displayName)
                            }
                        },
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("last_change_banner_text")
                    )
                    if (bilingualTimestamp != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = bilingualTimestamp,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.last_change_none_ta),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = bannerText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("last_change_banner_text")
                    )
                }
            }

            Icon(
                imageVector = if (historyExpanded) {
                    Icons.Filled.ExpandLess
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun formatBilingualTimestamp(timestampMillis: Long, todayTa: String, todayEn: String): String {
    val zoneId = ZoneId.systemDefault()
    val instant = Instant.ofEpochMilli(timestampMillis)
    val zonedDateTime = instant.atZone(zoneId)
    val today = LocalDate.now(zoneId)
    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)
    val dateFormatter = DateTimeFormatter.ofPattern("dd MMM, hh:mm a", Locale.US)
    val formattedTime = timeFormatter.format(zonedDateTime)
    return if (zonedDateTime.toLocalDate() == today) {
        "$todayTa $formattedTime | $todayEn $formattedTime"
    } else {
        dateFormatter.format(zonedDateTime)
    }
}

@Composable
private fun EmptyHistoryCard() {
    MagicalGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_history_card"),
        cornerRadius = 20.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.last_change_none_ta),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.history_empty_state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TransitionHistoryItemCard(
    event: TransitionEvent,
    isDark: Boolean
) {
    val todayTa = stringResource(R.string.today_ta)
    val todayEn = stringResource(R.string.today_en)
    val formattedTime = remember(event.timestampMillis, todayTa, todayEn) {
        formatBilingualTimestamp(event.timestampMillis, todayTa, todayEn)
    }
    val directionIcon = if (event.isUpgrade) {
        Icons.AutoMirrored.Filled.TrendingUp
    } else {
        Icons.AutoMirrored.Filled.TrendingDown
    }
    val directionLabel = stringResource(
        if (event.isUpgrade) R.string.direction_upgrade else R.string.direction_downgrade
    )
    val toAccent = generationAccentColor(event.toGeneration, isDark)

    MagicalGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_item_${event.id}"),
        cornerRadius = 18.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(toAccent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = directionIcon,
                    contentDescription = directionLabel,
                    tint = toAccent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${event.fromGeneration.displayName} \u2192 ${event.toGeneration.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$formattedTime • ${event.signalBarsAtTransition}/4",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun generationAccentColor(generation: NetworkGeneration, isDark: Boolean): Color {
    return when (generation) {
        NetworkGeneration.FIVE_G -> if (isDark) Gen5GAccentDark else Gen5GAccentLight
        NetworkGeneration.FOUR_G -> if (isDark) Gen4GAccentDark else Gen4GAccentLight
        NetworkGeneration.THREE_G -> if (isDark) Gen3GAccentDark else Gen3GAccentLight
        NetworkGeneration.TWO_G -> if (isDark) Gen2GAccentDark else Gen2GAccentLight
        NetworkGeneration.UNKNOWN -> if (isDark) GenUnknownAccentDark else GenUnknownAccentLight
    }
}
