package com.example

import com.example.domain.AlertSettings
import com.example.domain.BuiltInTone
import com.example.domain.Clock
import com.example.domain.NetworkGeneration
import com.example.domain.NetworkGenerationMapper
import com.example.domain.NetworkTransitionStateMachine
import com.example.domain.SettingsSerializer
import com.example.domain.SignalLevel
import com.example.domain.TransitionEvent
import com.example.domain.TransitionKey
import com.example.domain.TransitionRuleConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure Kotlin unit tests covering:
 * 1. Generation mapping for every TelephonyManager & TelephonyDisplayInfo constant.
 * 2. Debounce and duplicate-suppression state machine (flapping 5G<->4G, rapid 5G->4G->5G,
 *    unknown in between, service restart).
 * 3. Settings and history serialization/deserialization.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExampleUnitTest {

    // =========================================================================
    // 1. GENERATION MAPPING TESTS
    // =========================================================================

    @Test
    fun `maps 5G NR SA and all 5G NR override constants accurately`() {
        // 5G SA (Standalone NR)
        assertEquals(
            NetworkGeneration.FIVE_G,
            NetworkGenerationMapper.map(
                networkType = NetworkGenerationMapper.NETWORK_TYPE_NR,
                overrideNetworkType = NetworkGenerationMapper.OVERRIDE_NETWORK_TYPE_NONE
            )
        )

        // 5G NSA anchored on LTE
        assertEquals(
            NetworkGeneration.FIVE_G,
            NetworkGenerationMapper.map(
                networkType = NetworkGenerationMapper.NETWORK_TYPE_LTE,
                overrideNetworkType = NetworkGenerationMapper.OVERRIDE_NETWORK_TYPE_NR_NSA
            )
        )

        // 5G NSA mmWave / NR_ADVANCED anchored on LTE
        assertEquals(
            NetworkGeneration.FIVE_G,
            NetworkGenerationMapper.map(
                networkType = NetworkGenerationMapper.NETWORK_TYPE_LTE,
                overrideNetworkType = NetworkGenerationMapper.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE
            )
        )
        assertEquals(
            NetworkGeneration.FIVE_G,
            NetworkGenerationMapper.map(
                networkType = NetworkGenerationMapper.NETWORK_TYPE_LTE,
                overrideNetworkType = NetworkGenerationMapper.OVERRIDE_NETWORK_TYPE_NR_ADVANCED
            )
        )
    }

    @Test
    fun `maps LTE and LTE Advanced Pro variants to 4G`() {
        assertEquals(
            NetworkGeneration.FOUR_G,
            NetworkGenerationMapper.map(
                networkType = NetworkGenerationMapper.NETWORK_TYPE_LTE,
                overrideNetworkType = NetworkGenerationMapper.OVERRIDE_NETWORK_TYPE_NONE
            )
        )
        assertEquals(
            NetworkGeneration.FOUR_G,
            NetworkGenerationMapper.map(
                networkType = NetworkGenerationMapper.NETWORK_TYPE_LTE,
                overrideNetworkType = NetworkGenerationMapper.OVERRIDE_NETWORK_TYPE_LTE_CA
            )
        )
        assertEquals(
            NetworkGeneration.FOUR_G,
            NetworkGenerationMapper.map(
                networkType = NetworkGenerationMapper.NETWORK_TYPE_LTE,
                overrideNetworkType = NetworkGenerationMapper.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO
            )
        )
        assertEquals(
            NetworkGeneration.FOUR_G,
            NetworkGenerationMapper.map(
                networkType = NetworkGenerationMapper.NETWORK_TYPE_LTE_CA,
                overrideNetworkType = NetworkGenerationMapper.OVERRIDE_NETWORK_TYPE_NONE
            )
        )
    }

    @Test
    fun `maps all 3G UMTS HSPA EVDO EHRPD TD_SCDMA constants to 3G`() {
        val threeGTypes = listOf(
            NetworkGenerationMapper.NETWORK_TYPE_UMTS,
            NetworkGenerationMapper.NETWORK_TYPE_EVDO_0,
            NetworkGenerationMapper.NETWORK_TYPE_EVDO_A,
            NetworkGenerationMapper.NETWORK_TYPE_HSDPA,
            NetworkGenerationMapper.NETWORK_TYPE_HSUPA,
            NetworkGenerationMapper.NETWORK_TYPE_HSPA,
            NetworkGenerationMapper.NETWORK_TYPE_EVDO_B,
            NetworkGenerationMapper.NETWORK_TYPE_EHRPD,
            NetworkGenerationMapper.NETWORK_TYPE_HSPAP,
            NetworkGenerationMapper.NETWORK_TYPE_TD_SCDMA
        )
        for (type in threeGTypes) {
            assertEquals(
                "Expected 3G for constant $type",
                NetworkGeneration.THREE_G,
                NetworkGenerationMapper.map(
                    networkType = type,
                    overrideNetworkType = NetworkGenerationMapper.OVERRIDE_NETWORK_TYPE_NONE
                )
            )
        }
    }

    @Test
    fun `maps all 2G GPRS EDGE GSM CDMA 1xRTT IDEN constants to 2G`() {
        val twoGTypes = listOf(
            NetworkGenerationMapper.NETWORK_TYPE_GPRS,
            NetworkGenerationMapper.NETWORK_TYPE_EDGE,
            NetworkGenerationMapper.NETWORK_TYPE_CDMA,
            NetworkGenerationMapper.NETWORK_TYPE_1xRTT,
            NetworkGenerationMapper.NETWORK_TYPE_IDEN,
            NetworkGenerationMapper.NETWORK_TYPE_GSM
        )
        for (type in twoGTypes) {
            assertEquals(
                "Expected 2G for constant $type",
                NetworkGeneration.TWO_G,
                NetworkGenerationMapper.map(
                    networkType = type,
                    overrideNetworkType = NetworkGenerationMapper.OVERRIDE_NETWORK_TYPE_NONE
                )
            )
        }
    }

    @Test
    fun `maps UNKNOWN, IWLAN, missing permission, airplane mode, no SIM, and out-of-service to UNKNOWN`() {
        assertEquals(
            NetworkGeneration.UNKNOWN,
            NetworkGenerationMapper.map(
                networkType = NetworkGenerationMapper.NETWORK_TYPE_UNKNOWN,
                overrideNetworkType = NetworkGenerationMapper.OVERRIDE_NETWORK_TYPE_NONE
            )
        )
        assertEquals(
            NetworkGeneration.UNKNOWN,
            NetworkGenerationMapper.map(
                networkType = NetworkGenerationMapper.NETWORK_TYPE_IWLAN,
                overrideNetworkType = NetworkGenerationMapper.OVERRIDE_NETWORK_TYPE_NONE
            )
        )
        // Denied permission must always return UNKNOWN even if radio constants say NR
        assertEquals(
            NetworkGeneration.UNKNOWN,
            NetworkGenerationMapper.map(
                networkType = NetworkGenerationMapper.NETWORK_TYPE_NR,
                overrideNetworkType = NetworkGenerationMapper.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                hasPermission = false
            )
        )
        // Airplane mode must always return UNKNOWN
        assertEquals(
            NetworkGeneration.UNKNOWN,
            NetworkGenerationMapper.map(
                networkType = NetworkGenerationMapper.NETWORK_TYPE_LTE,
                overrideNetworkType = NetworkGenerationMapper.OVERRIDE_NETWORK_TYPE_NR_NSA,
                isAirplaneModeOn = true
            )
        )
        // No SIM must return UNKNOWN
        assertEquals(
            NetworkGeneration.UNKNOWN,
            NetworkGenerationMapper.map(
                networkType = NetworkGenerationMapper.NETWORK_TYPE_LTE,
                overrideNetworkType = NetworkGenerationMapper.OVERRIDE_NETWORK_TYPE_NONE,
                hasActiveSim = false
            )
        )
        // Out of service must return UNKNOWN
        assertEquals(
            NetworkGeneration.UNKNOWN,
            NetworkGenerationMapper.map(
                networkType = NetworkGenerationMapper.NETWORK_TYPE_LTE,
                overrideNetworkType = NetworkGenerationMapper.OVERRIDE_NETWORK_TYPE_NONE,
                isInService = false
            )
        )
    }

    // =========================================================================
    // 2. DEBOUNCE & DUPLICATE-SUPPRESSION STATE MACHINE TESTS
    // =========================================================================

    @Test
    fun `confirm transition only after debounce delay elapses`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        var fakeNow = 10_000L
        val confirmedEvents = mutableListOf<TransitionEvent>()

        val machine = NetworkTransitionStateMachine(
            scope = scope,
            clock = Clock { fakeNow },
            debounceMillisProvider = { 3_000L },
            onConfirmedTransition = { confirmedEvents += it }
        )

        machine.updateSignalBars(4)
        // First reading seeds baseline 5G without firing alert
        machine.onGenerationReported(NetworkGeneration.FIVE_G)
        runCurrent()
        assertTrue(confirmedEvents.isEmpty())
        assertEquals(NetworkGeneration.FIVE_G, machine.getSnapshot().lastConfirmedGeneration)

        // Switch to 4G
        fakeNow = 12_000L
        machine.updateSignalBars(2)
        machine.onGenerationReported(NetworkGeneration.FOUR_G)
        runCurrent()
        assertEquals(NetworkGeneration.FOUR_G, machine.getSnapshot().pendingTargetGeneration)
        assertTrue(confirmedEvents.isEmpty())

        // Advance 2,999ms (just shy of 3,000ms) -> still not fired
        fakeNow = 14_999L
        advanceTimeBy(2_999L)
        runCurrent()
        assertTrue(confirmedEvents.isEmpty())

        // Advance remaining 1ms -> confirmed!
        fakeNow = 15_000L
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(1, confirmedEvents.size)
        val event = confirmedEvents.first()
        assertEquals(NetworkGeneration.FIVE_G, event.fromGeneration)
        assertEquals(NetworkGeneration.FOUR_G, event.toGeneration)
        assertEquals(15_000L, event.timestampMillis)
        assertEquals(2, event.signalBarsAtTransition)
        assertEquals("Network changed: 5G \u2192 4G", event.summaryBanner)
    }

    @Test
    fun `rapid 5G to 4G to 5G revert within debounce window cancels alert`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        var fakeNow = 1_000L
        val confirmedEvents = mutableListOf<TransitionEvent>()

        val machine = NetworkTransitionStateMachine(
            scope = scope,
            clock = Clock { fakeNow },
            debounceMillisProvider = { 3_000L },
            onConfirmedTransition = { confirmedEvents += it }
        )

        machine.onGenerationReported(NetworkGeneration.FIVE_G)
        runCurrent()

        // Drop to 4G at t=2000
        fakeNow = 2_000L
        machine.onGenerationReported(NetworkGeneration.FOUR_G)
        advanceTimeBy(1_500L)
        runCurrent()
        assertEquals(NetworkGeneration.FOUR_G, machine.getSnapshot().pendingTargetGeneration)

        // Revert to 5G at t=3500 (after only 1.5s < 3.0s debounce)
        fakeNow = 3_500L
        machine.onGenerationReported(NetworkGeneration.FIVE_G)
        runCurrent()
        assertNull(machine.getSnapshot().pendingTargetGeneration)

        // Advance well past original 3s window -> zero alerts emitted
        advanceTimeBy(5_000L)
        runCurrent()
        assertTrue(confirmedEvents.isEmpty())
        assertEquals(NetworkGeneration.FIVE_G, machine.getSnapshot().lastConfirmedGeneration)
    }

    @Test
    fun `flapping 5G and 4G repeatedly under debounce window suppresses all spam`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        var fakeNow = 0L
        val confirmedEvents = mutableListOf<TransitionEvent>()

        val machine = NetworkTransitionStateMachine(
            scope = scope,
            clock = Clock { fakeNow },
            debounceMillisProvider = { 3_000L },
            onConfirmedTransition = { confirmedEvents += it }
        )

        machine.onGenerationReported(NetworkGeneration.FIVE_G)
        runCurrent()

        // Flap 5 times every 1 second
        repeat(5) {
            fakeNow += 1_000L
            machine.onGenerationReported(NetworkGeneration.FOUR_G)
            advanceTimeBy(1_000L)
            runCurrent()

            fakeNow += 1_000L
            machine.onGenerationReported(NetworkGeneration.FIVE_G)
            advanceTimeBy(1_000L)
            runCurrent()
        }

        assertTrue(confirmedEvents.isEmpty())

        // Finally settle on 4G for full 3s
        fakeNow += 1_000L
        machine.onGenerationReported(NetworkGeneration.FOUR_G)
        fakeNow += 3_000L
        advanceTimeBy(3_000L)
        runCurrent()

        assertEquals(1, confirmedEvents.size)
        assertEquals(NetworkGeneration.FIVE_G, confirmedEvents.single().fromGeneration)
        assertEquals(NetworkGeneration.FOUR_G, confirmedEvents.single().toGeneration)
    }

    @Test
    fun `unknown in between handles both return to 5G (silent) and handover to 4G (alerts 5G to 4G)`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        var fakeNow = 50_000L
        val confirmedEvents = mutableListOf<TransitionEvent>()

        val machine = NetworkTransitionStateMachine(
            scope = scope,
            clock = Clock { fakeNow },
            debounceMillisProvider = { 2_000L },
            onConfirmedTransition = { confirmedEvents += it }
        )

        // Baseline 5G
        machine.onGenerationReported(NetworkGeneration.FIVE_G)
        runCurrent()

        // Scenario A: 5G -> UNKNOWN -> 5G (e.g. brief elevator signal drop)
        machine.onGenerationReported(NetworkGeneration.UNKNOWN)
        runCurrent()
        assertEquals(NetworkGeneration.UNKNOWN, machine.getSnapshot().currentRawGeneration)
        assertEquals(NetworkGeneration.FIVE_G, machine.getSnapshot().lastConfirmedGeneration)

        advanceTimeBy(4_000L)
        machine.onGenerationReported(NetworkGeneration.FIVE_G)
        advanceTimeBy(4_000L)
        runCurrent()
        // Should NOT fire a redundant 5G -> 5G alert
        assertTrue(confirmedEvents.isEmpty())

        // Scenario B: 5G -> UNKNOWN -> 4G (handover gap)
        machine.onGenerationReported(NetworkGeneration.UNKNOWN)
        advanceTimeBy(1_000L)
        runCurrent()

        fakeNow = 60_000L
        machine.onGenerationReported(NetworkGeneration.FOUR_G)
        fakeNow = 62_000L
        advanceTimeBy(2_000L)
        runCurrent()

        assertEquals(1, confirmedEvents.size)
        assertEquals(NetworkGeneration.FIVE_G, confirmedEvents.single().fromGeneration)
        assertEquals(NetworkGeneration.FOUR_G, confirmedEvents.single().toGeneration)
    }

    @Test
    fun `service restart with seedInitialConfirmedGeneration avoids duplicate baseline alert`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val confirmedEvents = mutableListOf<TransitionEvent>()

        val machine = NetworkTransitionStateMachine(
            scope = scope,
            clock = Clock { 100_000L },
            debounceMillisProvider = { 1_000L },
            onConfirmedTransition = { confirmedEvents += it }
        )

        // Seed 4G on service restart
        machine.seedInitialConfirmedGeneration(NetworkGeneration.FOUR_G)
        // OS immediately delivers current 4G state callback
        machine.onGenerationReported(NetworkGeneration.FOUR_G)
        advanceTimeBy(2_000L)
        runCurrent()
        assertTrue(confirmedEvents.isEmpty())

        // Subsequent recovery to 5G triggers 4G -> 5G
        machine.onGenerationReported(NetworkGeneration.FIVE_G)
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(1, confirmedEvents.size)
        assertEquals(NetworkGeneration.FOUR_G, confirmedEvents.single().fromGeneration)
        assertEquals(NetworkGeneration.FIVE_G, confirmedEvents.single().toGeneration)
        assertTrue(confirmedEvents.single().isUpgrade)
    }

    // =========================================================================
    // 3. SETTINGS & HISTORY SERIALIZATION TESTS
    // =========================================================================

    @Test
    fun `settings serializer round-trips all fields and per-transition rules accurately`() {
        val customRules = AlertSettings.defaultRules().toMutableMap()
        customRules[TransitionKey.FIVE_G_TO_FOUR_G] = TransitionRuleConfig(
            key = TransitionKey.FIVE_G_TO_FOUR_G,
            enabled = true,
            tone = BuiltInTone.BEACON_ALERT
        )
        customRules[TransitionKey.FOUR_G_TO_THREE_G] = TransitionRuleConfig(
            key = TransitionKey.FOUR_G_TO_THREE_G,
            enabled = false,
            tone = BuiltInTone.CUSTOM_FILE
        )

        val original = AlertSettings(
            monitoringEnabled = true,
            masterSoundEnabled = false,
            hapticFeedbackEnabled = true,
            alertVolumePercent = 72,
            debounceMillis = 4_500L,
            useRecoverySoundOnUpgrade = false,
            customSoundFileName = "validated_custom_alert_sound.bin",
            customSoundDisplayName = "my_alert_tone.wav",
            dynamicColorEnabled = true,
            transitionRules = customRules
        )

        val encoded = SettingsSerializer.serializeSettings(original)
        val decoded = SettingsSerializer.deserializeSettings(encoded)

        assertEquals(original.monitoringEnabled, decoded.monitoringEnabled)
        assertEquals(original.masterSoundEnabled, decoded.masterSoundEnabled)
        assertEquals(original.hapticFeedbackEnabled, decoded.hapticFeedbackEnabled)
        assertEquals(original.alertVolumePercent, decoded.alertVolumePercent)
        assertEquals(original.debounceMillis, decoded.debounceMillis)
        assertEquals(original.useRecoverySoundOnUpgrade, decoded.useRecoverySoundOnUpgrade)
        assertEquals(original.customSoundFileName, decoded.customSoundFileName)
        assertEquals(original.customSoundDisplayName, decoded.customSoundDisplayName)
        assertEquals(original.dynamicColorEnabled, decoded.dynamicColorEnabled)

        val rule5g4g = decoded.transitionRules[TransitionKey.FIVE_G_TO_FOUR_G]
        assertNotNull(rule5g4g)
        assertEquals( true, rule5g4g?.enabled)
        assertEquals(BuiltInTone.BEACON_ALERT, rule5g4g?.tone)

        val rule4g3g = decoded.transitionRules[TransitionKey.FOUR_G_TO_THREE_G]
        assertNotNull(rule4g3g)
        assertFalse(rule4g3g!!.enabled)
        assertEquals(BuiltInTone.CUSTOM_FILE, rule4g3g.tone)
    }

    @Test
    fun `history serializer caps at 50 items and preserves order and metadata`() {
        val sixtyEvents = (1..60).map { idx ->
            TransitionEvent(
                id = "ev_$idx",
                fromGeneration = NetworkGeneration.FIVE_G,
                toGeneration = NetworkGeneration.FOUR_G,
                timestampMillis = 1_700_000_000_000L + idx * 1000L,
                signalBarsAtTransition = idx % 5,
                debounceUsedMillis = 3_000L
            )
        }

        val serialized = SettingsSerializer.serializeHistory(sixtyEvents)
        val deserialized = SettingsSerializer.deserializeHistory(serialized)

        assertEquals(50, deserialized.size)
        assertEquals("ev_1", deserialized.first().id)
        assertEquals("ev_50", deserialized.last().id)
        assertEquals(SignalLevel.GOOD, SignalLevel.fromLevel(3))
    }
}
