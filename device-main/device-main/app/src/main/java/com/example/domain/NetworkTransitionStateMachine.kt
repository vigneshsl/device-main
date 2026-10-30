package com.example.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Represents a confirmed, debounced cellular network generation transition.
 */
data class TransitionEvent(
    val id: String,
    val fromGeneration: NetworkGeneration,
    val toGeneration: NetworkGeneration,
    val timestampMillis: Long,
    val signalBarsAtTransition: Int,
    val debounceUsedMillis: Long
) {
    val isUpgrade: Boolean
        get() = toGeneration.rank > fromGeneration.rank && fromGeneration.isKnownGeneration && toGeneration.isKnownGeneration

    val isDowngrade: Boolean
        get() = toGeneration.rank < fromGeneration.rank && fromGeneration.isKnownGeneration && toGeneration.isKnownGeneration

    val summaryBanner: String
        get() = "Network changed: ${fromGeneration.displayName} \u2192 ${toGeneration.displayName}"
}

/**
 * Snapshot of the state machine for UI presentation and testing.
 */
data class TransitionMachineSnapshot(
    val currentRawGeneration: NetworkGeneration = NetworkGeneration.UNKNOWN,
    val lastConfirmedGeneration: NetworkGeneration = NetworkGeneration.UNKNOWN,
    val pendingTargetGeneration: NetworkGeneration? = null,
    val pendingSinceMillis: Long? = null
)

/**
 * Pure Kotlin event-driven state machine for cellular generation transitions.
 *
 * Key properties:
 * - Zero polling: only reacts when [onGenerationReported] is invoked by TelephonyCallback.
 * - Debounce with coroutine `delay(debounceMs)`: cancels pending transition if the network
 *   reverts to [lastConfirmedGeneration] before the timer expires.
 * - Duplicate suppression: never fires an alert if the stabilized generation equals
 *   [lastConfirmedGeneration].
 * - Unknown-in-between handling:
 *   - Immediately updates [currentRawGeneration] so the UI always displays honest "Unknown"
 *     during signal loss, airplane mode, or handover gaps.
 *   - Does not overwrite [lastConfirmedGeneration] with UNKNOWN unless UNKNOWN itself is
 *     explicitly configured or stabilized; if the radio briefly drops 5G -> UNKNOWN -> 5G,
 *     no false 5G->5G or UNKNOWN->5G alert is emitted, whereas 5G -> UNKNOWN -> 4G accurately
 *     emits 5G -> 4G once 4G stabilizes for [debounceMillis].
 * - Service restart resilience: can be seeded with [seedInitialConfirmedGeneration] so a process
 *   restart does not emit a spurious initial transition.
 */
class NetworkTransitionStateMachine(
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val debounceMillisProvider: () -> Long = { DEFAULT_DEBOUNCE_MILLIS },
    private val onStateSnapshotChanged: (TransitionMachineSnapshot) -> Unit = {},
    private val onConfirmedTransition: (TransitionEvent) -> Unit
) {
    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 3_000L
        const val MIN_DEBOUNCE_MILLIS = 500L
        const val MAX_DEBOUNCE_MILLIS = 15_000L
    }

    private var currentRawGeneration: NetworkGeneration = NetworkGeneration.UNKNOWN
    private var lastConfirmedGeneration: NetworkGeneration = NetworkGeneration.UNKNOWN
    private var pendingTargetGeneration: NetworkGeneration? = null
    private var pendingSinceMillis: Long? = null
    private var debounceJob: Job? = null
    private var latestSignalBars: Int = 0
    private var eventSequence: Long = 0L

    fun updateSignalBars(bars: Int) {
        latestSignalBars = bars.coerceIn(0, 4)
    }

    /**
     * Seeds or restores the baseline confirmed generation (e.g. when service starts or restarts)
     * without triggering a transition alert.
     */
    fun seedInitialConfirmedGeneration(generation: NetworkGeneration) {
        debounceJob?.cancel()
        debounceJob = null
        pendingTargetGeneration = null
        pendingSinceMillis = null
        currentRawGeneration = generation
        if (generation.isKnownGeneration) {
            lastConfirmedGeneration = generation
        }
        emitSnapshot()
    }

    /**
     * Resets the state machine when monitoring is turned OFF.
     */
    fun reset() {
        debounceJob?.cancel()
        debounceJob = null
        pendingTargetGeneration = null
        pendingSinceMillis = null
        currentRawGeneration = NetworkGeneration.UNKNOWN
        lastConfirmedGeneration = NetworkGeneration.UNKNOWN
        emitSnapshot()
    }

    /**
     * Invoked whenever [TelephonyCallback.DisplayInfoListener] or service state changes.
     */
    fun onGenerationReported(newGeneration: NetworkGeneration) {
        currentRawGeneration = newGeneration

        // Case 1: Radio temporarily reports UNKNOWN (handover gap, tunnel, airplane mode).
        // Cancel any pending debounce timer so a transient drop doesn't fire a stale transition,
        // while keeping lastConfirmedGeneration intact so recovery to the same generation is silent
        // and switch to a new generation (e.g. 5G -> Unknown -> 4G) triggers 5G -> 4G.
        if (!newGeneration.isKnownGeneration) {
            debounceJob?.cancel()
            debounceJob = null
            pendingTargetGeneration = null
            pendingSinceMillis = null
            emitSnapshot()
            return
        }

        // Case 2: First known cellular generation observed since monitoring started.
        // Establish baseline immediately without firing a false transition alert.
        if (!lastConfirmedGeneration.isKnownGeneration) {
            debounceJob?.cancel()
            debounceJob = null
            pendingTargetGeneration = null
            pendingSinceMillis = null
            lastConfirmedGeneration = newGeneration
            emitSnapshot()
            return
        }

        // Case 3: Network reverted back to the already-confirmed generation while a debounce
        // timer was running (e.g. rapid 5G -> 4G -> 5G within debounce window).
        // Cancel the pending timer and suppress duplicate alert.
        if (newGeneration == lastConfirmedGeneration) {
            if (debounceJob != null || pendingTargetGeneration != null) {
                debounceJob?.cancel()
                debounceJob = null
                pendingTargetGeneration = null
                pendingSinceMillis = null
            }
            emitSnapshot()
            return
        }

        // Case 4: New known generation differs from lastConfirmedGeneration.
        // If we are already debouncing toward this exact same target generation, keep the existing
        // timer running (do not reset it on duplicate callback emissions).
        if (pendingTargetGeneration == newGeneration && debounceJob?.isActive == true) {
            emitSnapshot()
            return
        }

        // Otherwise, cancel any previous pending target (e.g. 5G -> 4G -> 3G before timer finished)
        // and start a fresh coroutine delay for the new target generation.
        debounceJob?.cancel()
        val debounceMs = debounceMillisProvider().coerceIn(0L, MAX_DEBOUNCE_MILLIS)
        val startedAt = clock.nowMillis()
        pendingTargetGeneration = newGeneration
        pendingSinceMillis = startedAt
        emitSnapshot()

        debounceJob = scope.launch {
            if (debounceMs > 0L) {
                delay(debounceMs)
            }
            confirmTransition(targetGeneration = newGeneration, debounceUsedMs = debounceMs)
        }
    }

    private fun confirmTransition(targetGeneration: NetworkGeneration, debounceUsedMs: Long) {
        val previous = lastConfirmedGeneration
        pendingTargetGeneration = null
        pendingSinceMillis = null
        debounceJob = null

        if (!previous.isKnownGeneration || !targetGeneration.isKnownGeneration || previous == targetGeneration) {
            lastConfirmedGeneration = targetGeneration
            emitSnapshot()
            return
        }

        lastConfirmedGeneration = targetGeneration
        val now = clock.nowMillis()
        eventSequence += 1
        val event = TransitionEvent(
            id = "${now}_$eventSequence",
            fromGeneration = previous,
            toGeneration = targetGeneration,
            timestampMillis = now,
            signalBarsAtTransition = latestSignalBars,
            debounceUsedMillis = debounceUsedMs
        )
        emitSnapshot()
        onConfirmedTransition(event)
    }

    fun getSnapshot(): TransitionMachineSnapshot = TransitionMachineSnapshot(
        currentRawGeneration = currentRawGeneration,
        lastConfirmedGeneration = lastConfirmedGeneration,
        pendingTargetGeneration = pendingTargetGeneration,
        pendingSinceMillis = pendingSinceMillis
    )

    private fun emitSnapshot() {
        onStateSnapshotChanged(getSnapshot())
    }
}
