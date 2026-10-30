package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.AlertSettings
import com.example.domain.BuiltInTone
import com.example.domain.NetworkGeneration
import com.example.domain.TransitionKey
import com.example.domain.TransitionRuleConfig
import com.example.ui.theme.BilingualText
import com.example.ui.theme.MagicalGlassCard
import com.example.ui.theme.MagicalIconCircle
import com.example.ui.theme.MagicalScreenTopBar
import com.example.ui.theme.MagicalStatusPill
import com.example.ui.theme.MagicalSwitchTrackOn
import com.example.ui.theme.MagicalWaveBackdrop
import com.example.ui.theme.Pill2GRedBg
import com.example.ui.theme.Pill2GRedText
import com.example.ui.theme.Pill3GPurpleBg
import com.example.ui.theme.Pill3GPurpleText
import com.example.ui.theme.Pill4GBlueBg
import com.example.ui.theme.Pill4GBlueText
import com.example.ui.theme.Pill4GOrangeBg
import com.example.ui.theme.Pill4GOrangeText
import com.example.ui.theme.Pill5GBg
import com.example.ui.theme.Pill5GText
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val PRIMARY_TRANSITION_KEYS = listOf(
    TransitionKey.FIVE_G_TO_FOUR_G,
    TransitionKey.FOUR_G_TO_FIVE_G,
    TransitionKey.FOUR_G_TO_THREE_G,
    TransitionKey.THREE_G_TO_FOUR_G
)

@Composable
fun SettingsScreen(
    settings: AlertSettings,
    onMasterSoundChanged: (Boolean) -> Unit = {},
    onHapticChanged: (Boolean) -> Unit,
    onVolumeChanged: (Int) -> Unit,
    onDebounceChanged: (Long) -> Unit,
    onRecoverySoundChanged: (Boolean) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit = {},
    onPickCustomSound: () -> Unit,
    onRemoveCustomSound: () -> Unit,
    onUpdateTransitionRule: (TransitionKey, Boolean, BuiltInTone) -> Unit,
    onTestTransitionTone: (TransitionKey) -> Unit,
    onPreviewDefaultSound: () -> Unit = { onTestTransitionTone(TransitionKey.FOUR_G_TO_FIVE_G) },
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showSoundPickerDialog by rememberSaveable { mutableStateOf(false) }
    var showAllTransitions by rememberSaveable { mutableStateOf(false) }

    if (showSoundPickerDialog) {
        SoundSelectionDialog(
            customSoundDisplayName = settings.customSoundDisplayName,
            onUseDefault = {
                if (settings.customSoundDisplayName != null) {
                    onRemoveCustomSound()
                }
                showSoundPickerDialog = false
            },
            onPickCustom = {
                showSoundPickerDialog = false
                onPickCustomSound()
            },
            onDismiss = { showSoundPickerDialog = false }
        )
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
                .testTag("settings_lazy_column"),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Bar: < Back + அலர்ட் ஒலி அமைப்புகள் / Alerts & Audio
            item {
                MagicalScreenTopBar(
                    tamilTitle = stringResource(R.string.settings_screen_title_ta),
                    englishTitle = stringResource(R.string.settings_screen_title),
                    onBackClick = onBackClick
                )
            }

            // Card 1: Alert Sound, Selected Sound Row, Volume Slider, Test Sound Button
            item {
                AlertSoundAndVolumeCard(
                    settings = settings,
                    onOpenSoundSelector = { showSoundPickerDialog = true },
                    onVolumeChanged = onVolumeChanged,
                    onPreviewDefaultSound = onPreviewDefaultSound
                )
            }

            // Card 2: Alert for Network Changes (நெட்வொர்க் மாற்றங்களுக்கு அலர்ட்)
            item {
                NetworkTransitionsCard(
                    settings = settings,
                    showAllTransitions = showAllTransitions,
                    onToggleShowAll = { showAllTransitions = !showAllTransitions },
                    onUpdateTransitionRule = onUpdateTransitionRule,
                    onTestTransitionTone = onTestTransitionTone
                )
            }

            // Card 3: Debounce & Haptics Settings
            item {
                DebounceAndHapticsCard(
                    settings = settings,
                    onDebounceChanged = onDebounceChanged,
                    onHapticChanged = onHapticChanged,
                    onRecoverySoundChanged = onRecoverySoundChanged
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AlertSoundAndVolumeCard(
    settings: AlertSettings,
    onOpenSoundSelector: () -> Unit,
    onVolumeChanged: (Int) -> Unit,
    onPreviewDefaultSound: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val hasCustomSound = !settings.customSoundDisplayName.isNullOrBlank()
    val volumeDesc = stringResource(R.string.volume_cd_format, settings.alertVolumePercent)
    val soundName = settings.customSoundDisplayName ?: stringResource(R.string.settings_default_sound_name)
    val badgeText = if (hasCustomSound) {
        stringResource(R.string.settings_custom_badge)
    } else {
        stringResource(R.string.settings_default_badge)
    }

    MagicalGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("master_audio_card"),
        cornerRadius = 26.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Alert Sound Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MagicalIconCircle(
                    icon = Icons.Filled.Notifications,
                    size = 44.dp,
                    iconSize = 22.dp
                )
                BilingualText(
                    tamil = stringResource(R.string.alert_sound_title_ta),
                    english = stringResource(R.string.alert_sound_title),
                    tamilStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    englishStyle = MaterialTheme.typography.bodySmall
                )
            }

            // 2. Selected Sound Selector Pill Row (Crystal Bell [Default] >)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isDark) Color(0xFF182C21) else Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenSoundSelector)
                    .testTag("custom_sound_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = soundName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("custom_sound_status_text")
                        )
                        MagicalStatusPill(
                            text = badgeText,
                            isPositive = true
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.settings_select_sound),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 3. Volume Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MagicalIconCircle(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    size = 44.dp,
                    iconSize = 22.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    BilingualText(
                        tamil = stringResource(R.string.settings_volume_title_ta),
                        english = stringResource(R.string.settings_volume_title),
                        tamilStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                        englishStyle = MaterialTheme.typography.bodySmall
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Slider(
                            value = settings.alertVolumePercent.toFloat(),
                            onValueChange = { onVolumeChanged(it.roundToInt()) },
                            valueRange = 0f..100f,
                            steps = 19,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color(0xFF22C55E),
                                inactiveTrackColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .semantics {
                                    contentDescription = volumeDesc
                                }
                                .testTag("volume_slider")
                        )

                        Text(
                            text = "${settings.alertVolumePercent}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 4. Test Sound Pill Button (ஒலியைச் சோதிக்கவும் / Test Sound)
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isDark) Color(0xFF1A3325) else Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onPreviewDefaultSound)
                    .minimumInteractiveComponentSize()
                    .testTag("preview_default_sound_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF16A34A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    BilingualText(
                        tamil = stringResource(R.string.settings_test_sound_title_ta),
                        english = stringResource(R.string.settings_test_sound_title),
                        tamilStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        englishStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkTransitionsCard(
    settings: AlertSettings,
    showAllTransitions: Boolean,
    onToggleShowAll: () -> Unit,
    onUpdateTransitionRule: (TransitionKey, Boolean, BuiltInTone) -> Unit,
    onTestTransitionTone: (TransitionKey) -> Unit
) {
    val displayedKeys = if (showAllTransitions) {
        TransitionKey.entries
    } else {
        PRIMARY_TRANSITION_KEYS
    }

    MagicalGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("transitions_card"),
        cornerRadius = 26.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MagicalIconCircle(
                    icon = Icons.Filled.CellTower,
                    size = 38.dp,
                    iconSize = 20.dp
                )
                BilingualText(
                    tamil = stringResource(R.string.settings_transitions_title_ta),
                    english = stringResource(R.string.settings_transitions_title),
                    tamilStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                    englishStyle = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            displayedKeys.forEachIndexed { index, key ->
                val rule = settings.transitionRules[key] ?: TransitionRuleConfig(key)
                TransitionPillRow(
                    key = key,
                    rule = rule,
                    hasCustomSound = settings.customSoundFileName != null,
                    onUpdateRule = { enabled, tone -> onUpdateTransitionRule(key, enabled, tone) },
                    onTestTone = { onTestTransitionTone(key) }
                )
                if (index < displayedKeys.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                }
            }

            TextButton(
                onClick = onToggleShowAll,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .testTag("toggle_all_transitions_button")
            ) {
                Icon(
                    imageVector = if (showAllTransitions) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (showAllTransitions) {
                        stringResource(R.string.transitions_show_primary)
                    } else {
                        stringResource(R.string.transitions_show_all, TransitionKey.entries.size)
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun TransitionPillRow(
    key: TransitionKey,
    rule: TransitionRuleConfig,
    hasCustomSound: Boolean,
    onUpdateRule: (Boolean, BuiltInTone) -> Unit,
    onTestTone: () -> Unit
) {
    var toneMenuExpanded by remember { mutableStateOf(false) }
    val testToneDesc = stringResource(R.string.test_tone_cd_format, key.label)

    val (fromBg, fromFg) = pillColorsForGeneration(key.from, isSource = true, target = key.to)
    val (toBg, toFg) = pillColorsForGeneration(key.to, isSource = false, target = key.from)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("transition_row_${key.keyId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Pastel generation pills (e.g. [5G] -> [4G]) — clickable to pick specific tone
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .alpha(if (rule.enabled) 1f else 0.45f)
                .clip(RoundedCornerShape(14.dp))
                .clickable { toneMenuExpanded = true }
                .padding(vertical = 4.dp)
                .testTag("tone_picker_${key.keyId}")
        ) {
            GenerationColoredPill(
                text = key.from.displayName,
                backgroundColor = fromBg,
                textColor = fromFg
            )

            Text(
                text = "\u2192",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = toFg
            )

            GenerationColoredPill(
                text = key.to.displayName,
                backgroundColor = toBg,
                textColor = toFg
            )

            DropdownMenu(
                expanded = toneMenuExpanded,
                onDismissRequest = { toneMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(
                                if (rule.enabled) R.string.rule_enabled else R.string.rule_disabled
                            ),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    onClick = {
                        toneMenuExpanded = false
                        onUpdateRule(!rule.enabled, rule.tone)
                    }
                )
                HorizontalDivider()
                BuiltInTone.entries.forEach { toneOption ->
                    val enabledOption = toneOption != BuiltInTone.CUSTOM_FILE || hasCustomSound
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = toneOption.displayName,
                                    fontWeight = if (rule.tone == toneOption) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = toneOption.description,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        enabled = enabledOption,
                        onClick = {
                            toneMenuExpanded = false
                            onUpdateRule(true, toneOption)
                        }
                    )
                }
            }
        }

        // Right: Play preview circle button ▶ matching reference image
        Surface(
            shape = CircleShape,
            color = if (rule.enabled) Color(0xFFDCFCE7) else Color(0xFFF1F5F9),
            border = BorderStroke(1.dp, Color(0xFF86EFAC).copy(alpha = 0.6f)),
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onTestTone)
                .semantics {
                    contentDescription = testToneDesc
                }
                .testTag("test_btn_${key.keyId}")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = if (rule.enabled) Color(0xFF15803D) else Color(0xFF64748B),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun GenerationColoredPill(
    text: String,
    backgroundColor: Color,
    textColor: Color
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(50),
        modifier = Modifier.widthIn(min = 54.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

private fun pillColorsForGeneration(
    generation: NetworkGeneration,
    isSource: Boolean,
    target: NetworkGeneration
): Pair<Color, Color> {
    return when (generation) {
        NetworkGeneration.FIVE_G -> Pill5GBg to Pill5GText
        NetworkGeneration.FOUR_G -> {
            if (!isSource && target == NetworkGeneration.FIVE_G) {
                Pill4GOrangeBg to Pill4GOrangeText
            } else if (isSource && target == NetworkGeneration.THREE_G) {
                Pill4GOrangeBg to Pill4GOrangeText
            } else {
                Pill4GBlueBg to Pill4GBlueText
            }
        }
        NetworkGeneration.THREE_G -> Pill3GPurpleBg to Pill3GPurpleText
        NetworkGeneration.TWO_G -> Pill2GRedBg to Pill2GRedText
        NetworkGeneration.UNKNOWN -> Color(0xFFF1F5F9) to Color(0xFF475569)
    }
}

@Composable
private fun DebounceAndHapticsCard(
    settings: AlertSettings,
    onDebounceChanged: (Long) -> Unit,
    onHapticChanged: (Boolean) -> Unit,
    onRecoverySoundChanged: (Boolean) -> Unit
) {
    val debounceSeconds = settings.debounceMillis / 1000f
    val debounceDesc = stringResource(R.string.debounce_cd_format, debounceSeconds)

    MagicalGlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MagicalIconCircle(
                    icon = Icons.Filled.Timer,
                    size = 40.dp,
                    iconSize = 20.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    BilingualText(
                        tamil = stringResource(R.string.settings_debounce_title_ta),
                        english = stringResource(R.string.settings_debounce_label, debounceSeconds),
                        tamilStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                        englishStyle = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Slider(
                value = debounceSeconds,
                onValueChange = { seconds ->
                    val snappedMs = ((seconds * 2f).roundToLong() * 500L).coerceIn(500L, 15_000L)
                    onDebounceChanged(snappedMs)
                },
                valueRange = 0.5f..15.0f,
                steps = 28,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF22C55E),
                    inactiveTrackColor = Color(0xFFE2E8F0)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .semantics {
                        contentDescription = debounceDesc
                    }
                    .testTag("debounce_slider")
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            // Haptic feedback toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BilingualText(
                    tamil = stringResource(R.string.settings_haptic_label_ta),
                    english = stringResource(R.string.settings_haptic_label),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = settings.hapticFeedbackEnabled,
                    onCheckedChange = onHapticChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MagicalSwitchTrackOn
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("haptic_switch")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            // Upgrade chime toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BilingualText(
                    tamil = stringResource(R.string.settings_recovery_sound_label_ta),
                    english = stringResource(R.string.settings_recovery_sound_label),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = settings.useRecoverySoundOnUpgrade,
                    onCheckedChange = onRecoverySoundChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MagicalSwitchTrackOn
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("recovery_sound_switch")
                )
            }
        }
    }
}

@Composable
private fun SoundSelectionDialog(
    customSoundDisplayName: String?,
    onUseDefault: () -> Unit,
    onPickCustom: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            BilingualText(
                tamil = stringResource(R.string.settings_select_sound_ta),
                english = stringResource(R.string.settings_select_sound),
                tamilStyle = MaterialTheme.typography.titleLarge,
                englishStyle = MaterialTheme.typography.bodyMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Default Crystal Bell option
                OutlinedButton(
                    onClick = onUseDefault,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("select_default_sound_button")
                ) {
                    Text(stringResource(R.string.select_default_sound))
                }

                // Import Custom Audio option
                FilledTonalButton(
                    onClick = onPickCustom,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("pick_custom_sound_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_custom_sound_pick))
                }

                if (customSoundDisplayName != null) {
                    OutlinedButton(
                        onClick = onUseDefault,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("remove_custom_sound_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_custom_sound_remove))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close_button))
            }
        }
    )
}
