package com.example.domain

/**
 * Pure Kotlin serializer/deserializer for [AlertSettings] and [TransitionEvent] history list
 * using deterministic pipe/line encoding so settings & history persistence can be unit-tested
 * without mocking DataStore internals, while also backing our DataStore Preferences storage.
 */
object SettingsSerializer {

    private const val KV_SEPARATOR = "="
    private const val ENTRY_SEPARATOR = ";"
    private const val HISTORY_FIELD_SEP = "|"
    const val MAX_HISTORY_ITEMS = 50

    fun serializeSettings(settings: AlertSettings): String {
        val parts = mutableListOf<String>()
        parts += "monitoringEnabled${KV_SEPARATOR}${settings.monitoringEnabled}"
        parts += "masterSoundEnabled${KV_SEPARATOR}${settings.masterSoundEnabled}"
        parts += "hapticFeedbackEnabled${KV_SEPARATOR}${settings.hapticFeedbackEnabled}"
        parts += "alertVolumePercent${KV_SEPARATOR}${settings.alertVolumePercent.coerceIn(0, 100)}"
        parts += "debounceMillis${KV_SEPARATOR}${settings.debounceMillis.coerceIn(500L, 15_000L)}"
        parts += "useRecoverySoundOnUpgrade${KV_SEPARATOR}${settings.useRecoverySoundOnUpgrade}"
        parts += "customSoundFileName${KV_SEPARATOR}${sanitize(settings.customSoundFileName.orEmpty())}"
        parts += "customSoundDisplayName${KV_SEPARATOR}${sanitize(settings.customSoundDisplayName.orEmpty())}"
        parts += "dynamicColorEnabled${KV_SEPARATOR}${settings.dynamicColorEnabled}"

        TransitionKey.entries.forEach { key ->
            val rule = settings.transitionRules[key] ?: TransitionRuleConfig(key)
            parts += "rule_${key.keyId}_enabled${KV_SEPARATOR}${rule.enabled}"
            parts += "rule_${key.keyId}_tone${KV_SEPARATOR}${rule.tone.id}"
        }
        return parts.joinToString(ENTRY_SEPARATOR)
    }

    fun deserializeSettings(serialized: String): AlertSettings {
        if (serialized.isBlank()) return AlertSettings()
        val map = serialized.split(ENTRY_SEPARATOR)
            .mapNotNull { token ->
                val idx = token.indexOf(KV_SEPARATOR)
                if (idx <= 0) null else token.substring(0, idx) to token.substring(idx + 1)
            }
            .toMap()

        val rules = TransitionKey.entries.associateWith { key ->
            val enabled = map["rule_${key.keyId}_enabled"]?.toBooleanStrictOrNull() ?: key.defaultEnabled
            val tone = BuiltInTone.fromId(map["rule_${key.keyId}_tone"] ?: key.defaultTone.id)
            TransitionRuleConfig(key = key, enabled = enabled, tone = tone)
        }

        val customFile = map["customSoundFileName"]?.takeIf { it.isNotBlank() }
        val customDisplay = map["customSoundDisplayName"]?.takeIf { it.isNotBlank() }

        return AlertSettings(
            monitoringEnabled = map["monitoringEnabled"]?.toBooleanStrictOrNull() ?: false,
            masterSoundEnabled = map["masterSoundEnabled"]?.toBooleanStrictOrNull() ?: true,
            hapticFeedbackEnabled = map["hapticFeedbackEnabled"]?.toBooleanStrictOrNull() ?: true,
            alertVolumePercent = (map["alertVolumePercent"]?.toIntOrNull() ?: 85).coerceIn(0, 100),
            debounceMillis = (map["debounceMillis"]?.toLongOrNull()
                ?: NetworkTransitionStateMachine.DEFAULT_DEBOUNCE_MILLIS).coerceIn(500L, 15_000L),
            useRecoverySoundOnUpgrade = map["useRecoverySoundOnUpgrade"]?.toBooleanStrictOrNull() ?: true,
            customSoundFileName = customFile,
            customSoundDisplayName = customDisplay,
            dynamicColorEnabled = map["dynamicColorEnabled"]?.toBooleanStrictOrNull() ?: false,
            transitionRules = rules
        )
    }

    fun serializeHistory(events: List<TransitionEvent>): String {
        return events.take(MAX_HISTORY_ITEMS).joinToString("\n") { ev ->
            listOf(
                sanitize(ev.id),
                ev.fromGeneration.name,
                ev.toGeneration.name,
                ev.timestampMillis.toString(),
                ev.signalBarsAtTransition.toString(),
                ev.debounceUsedMillis.toString()
            ).joinToString(HISTORY_FIELD_SEP)
        }
    }

    fun deserializeHistory(raw: String): List<TransitionEvent> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split(HISTORY_FIELD_SEP)
                if (parts.size < 6) return@mapNotNull null
                val from = runCatching { NetworkGeneration.valueOf(parts[1]) }.getOrNull() ?: return@mapNotNull null
                val to = runCatching { NetworkGeneration.valueOf(parts[2]) }.getOrNull() ?: return@mapNotNull null
                val timestamp = parts[3].toLongOrNull() ?: return@mapNotNull null
                val bars = parts[4].toIntOrNull()?.coerceIn(0, 4) ?: 0
                val debounce = parts[5].toLongOrNull() ?: NetworkTransitionStateMachine.DEFAULT_DEBOUNCE_MILLIS
                TransitionEvent(
                    id = parts[0],
                    fromGeneration = from,
                    toGeneration = to,
                    timestampMillis = timestamp,
                    signalBarsAtTransition = bars,
                    debounceUsedMillis = debounce
                )
            }
            .take(MAX_HISTORY_ITEMS)
            .toList()
    }

    private fun sanitize(input: String): String =
        input.replace(ENTRY_SEPARATOR, "_").replace(KV_SEPARATOR, "_").replace(HISTORY_FIELD_SEP, "_").replace("\n", " ")
}
