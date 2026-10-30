package com.example.domain

/**
 * Built-in synthesized acoustic alert tones (zero external asset dependency, crisp PCM synthesis)
 * plus optional validated user-imported custom sound file.
 */
enum class BuiltInTone(
    val id: String,
    val displayName: String,
    val description: String,
    val frequenciesHz: IntArray,
    val noteDurationMs: Int
) {
    PULSE_DOWN(
        id = "PULSE_DOWN",
        displayName = "Sonar Downgrade Pulse",
        description = "Descending dual-tone (880 Hz \u2192 587 Hz) for generation drops",
        frequenciesHz = intArrayOf(880, 587),
        noteDurationMs = 140
    ),
    RADAR_CHIME(
        id = "RADAR_CHIME",
        displayName = "Precision Radar Chime",
        description = "Ascending triad (523 Hz \u2192 659 Hz \u2192 784 Hz) for clean handovers",
        frequenciesHz = intArrayOf(523, 659, 784),
        noteDurationMs = 110
    ),
    BEACON_ALERT(
        id = "BEACON_ALERT",
        displayName = "Tactical Double Beep",
        description = "Sharp 1046 Hz double staccato pulse for high visibility",
        frequenciesHz = intArrayOf(1046, 0, 1046),
        noteDurationMs = 95
    ),
    LOW_CARRIER_WARN(
        id = "LOW_CARRIER_WARN",
        displayName = "Legacy Radio Warning",
        description = "Deep three-step alert (784 Hz \u2192 523 Hz \u2192 392 Hz) for 3G/2G fallback",
        frequenciesHz = intArrayOf(784, 523, 392),
        noteDurationMs = 135
    ),
    RECOVERY_UPGRADE(
        id = "RECOVERY_UPGRADE",
        displayName = "5G Recovery Harmonic",
        description = "Bright harmonic octave (587 Hz \u2192 880 Hz \u2192 1175 Hz)",
        frequenciesHz = intArrayOf(587, 880, 1175),
        noteDurationMs = 105
    ),
    CUSTOM_FILE(
        id = "CUSTOM_FILE",
        displayName = "Custom Imported Audio",
        description = "Plays your validated private-storage audio file (falls back to Radar Chime if none)",
        frequenciesHz = intArrayOf(523, 659, 784),
        noteDurationMs = 110
    );

    companion object {
        fun fromId(id: String?): BuiltInTone =
            entries.firstOrNull { it.id == id } ?: PULSE_DOWN
    }
}

/**
 * Defines all 12 directional transitions between known cellular generations (5G, 4G, 3G, 2G).
 */
enum class TransitionKey(
    val keyId: String,
    val from: NetworkGeneration,
    val to: NetworkGeneration,
    val defaultEnabled: Boolean,
    val defaultTone: BuiltInTone
) {
    FIVE_G_TO_FOUR_G("5G_TO_4G", NetworkGeneration.FIVE_G, NetworkGeneration.FOUR_G, true, BuiltInTone.PULSE_DOWN),
    FIVE_G_TO_THREE_G("5G_TO_3G", NetworkGeneration.FIVE_G, NetworkGeneration.THREE_G, true, BuiltInTone.LOW_CARRIER_WARN),
    FIVE_G_TO_TWO_G("5G_TO_2G", NetworkGeneration.FIVE_G, NetworkGeneration.TWO_G, true, BuiltInTone.LOW_CARRIER_WARN),

    FOUR_G_TO_FIVE_G("4G_TO_5G", NetworkGeneration.FOUR_G, NetworkGeneration.FIVE_G, true, BuiltInTone.RECOVERY_UPGRADE),
    FOUR_G_TO_THREE_G("4G_TO_3G", NetworkGeneration.FOUR_G, NetworkGeneration.THREE_G, true, BuiltInTone.LOW_CARRIER_WARN),
    FOUR_G_TO_TWO_G("4G_TO_2G", NetworkGeneration.FOUR_G, NetworkGeneration.TWO_G, true, BuiltInTone.LOW_CARRIER_WARN),

    THREE_G_TO_FIVE_G("3G_TO_5G", NetworkGeneration.THREE_G, NetworkGeneration.FIVE_G, true, BuiltInTone.RECOVERY_UPGRADE),
    THREE_G_TO_FOUR_G("3G_TO_4G", NetworkGeneration.THREE_G, NetworkGeneration.FOUR_G, true, BuiltInTone.RADAR_CHIME),
    THREE_G_TO_TWO_G("3G_TO_2G", NetworkGeneration.THREE_G, NetworkGeneration.TWO_G, true, BuiltInTone.BEACON_ALERT),

    TWO_G_TO_FIVE_G("2G_TO_5G", NetworkGeneration.TWO_G, NetworkGeneration.FIVE_G, true, BuiltInTone.RECOVERY_UPGRADE),
    TWO_G_TO_FOUR_G("2G_TO_4G", NetworkGeneration.TWO_G, NetworkGeneration.FOUR_G, true, BuiltInTone.RADAR_CHIME),
    TWO_G_TO_THREE_G("2G_TO_3G", NetworkGeneration.TWO_G, NetworkGeneration.THREE_G, false, BuiltInTone.RADAR_CHIME);

    val label: String
        get() = "${from.displayName} \u2192 ${to.displayName}"

    val isUpgrade: Boolean
        get() = to.rank > from.rank

    companion object {
        fun find(from: NetworkGeneration, to: NetworkGeneration): TransitionKey? =
            entries.firstOrNull { it.from == from && it.to == to }
    }
}

/**
 * Per-transition user configuration (enabled switch + chosen tone).
 */
data class TransitionRuleConfig(
    val key: TransitionKey,
    val enabled: Boolean = key.defaultEnabled,
    val tone: BuiltInTone = key.defaultTone
)

/**
 * Complete user settings persisted in DataStore and serializable for deterministic testing.
 */
data class AlertSettings(
    val monitoringEnabled: Boolean = false,
    val masterSoundEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val alertVolumePercent: Int = 85,
    val debounceMillis: Long = NetworkTransitionStateMachine.DEFAULT_DEBOUNCE_MILLIS,
    val useRecoverySoundOnUpgrade: Boolean = true,
    val customSoundFileName: String? = null,
    val customSoundDisplayName: String? = null,
    val dynamicColorEnabled: Boolean = false,
    val transitionRules: Map<TransitionKey, TransitionRuleConfig> = defaultRules()
) {
    companion object {
        fun defaultRules(): Map<TransitionKey, TransitionRuleConfig> =
            TransitionKey.entries.associateWith { key ->
                TransitionRuleConfig(
                    key = key,
                    enabled = key.defaultEnabled,
                    tone = key.defaultTone
                )
            }
    }

    fun ruleFor(from: NetworkGeneration, to: NetworkGeneration): TransitionRuleConfig? {
        val key = TransitionKey.find(from, to) ?: return null
        return transitionRules[key] ?: TransitionRuleConfig(key)
    }

    /**
     * Resolves which [BuiltInTone] should play for a given transition, honoring
     * [useRecoverySoundOnUpgrade] if enabled and the transition is an upgrade.
     */
    fun resolveToneFor(from: NetworkGeneration, to: NetworkGeneration): BuiltInTone? {
        if (!masterSoundEnabled) return null
        val rule = ruleFor(from, to) ?: return null
        if (!rule.enabled) return null
        if (rule.tone == BuiltInTone.CUSTOM_FILE && customSoundFileName != null) {
            return BuiltInTone.CUSTOM_FILE
        }
        if (useRecoverySoundOnUpgrade && to.rank > from.rank && rule.tone == rule.key.defaultTone) {
            return BuiltInTone.RECOVERY_UPGRADE
        }
        return rule.tone
    }
}
