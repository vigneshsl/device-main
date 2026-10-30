package com.example.domain

/**
 * Represents the normalized cellular network generation.
 * Includes rank ordering so transitions can be classified as upgrades (recovery) or downgrades.
 */
enum class NetworkGeneration(
    val displayName: String,
    val shortBadge: String,
    val orbBadge: String,
    val rank: Int,
    val technicalSummary: String
) {
    FIVE_G(
        displayName = "5G",
        shortBadge = "5G NR",
        orbBadge = "NR NSA",
        rank = 5,
        technicalSummary = "5G New Radio (NR SA / NR NSA / mmWave Advanced)"
    ),
    FOUR_G(
        displayName = "4G",
        shortBadge = "4G LTE",
        orbBadge = "4G LTE",
        rank = 4,
        technicalSummary = "4G Long Term Evolution (LTE / LTE-A)"
    ),
    THREE_G(
        displayName = "3G",
        shortBadge = "3G HSPA",
        orbBadge = "3G HSPA",
        rank = 3,
        technicalSummary = "3G UMTS / HSPA / HSDPA / HSUPA / HSPAP / EVDO / TD-SCDMA"
    ),
    TWO_G(
        displayName = "2G",
        shortBadge = "2G EDGE",
        orbBadge = "2G EDGE",
        rank = 2,
        technicalSummary = "2G GSM / GPRS / EDGE / CDMA / 1xRTT / IDEN"
    ),
    UNKNOWN(
        displayName = "Unknown",
        shortBadge = "NO LINK",
        orbBadge = "NO LINK",
        rank = 0,
        technicalSummary = "No SIM, No Service, Airplane Mode, or Permission Denied"
    );

    val isKnownGeneration: Boolean
        get() = this != UNKNOWN
}

/**
 * Normalized signal strength bar level (0 to 4) reported by SignalStrengthsListener.
 */
enum class SignalLevel(val bars: Int, val label: String) {
    NONE_OR_UNKNOWN(0, "None / Unknown"),
    POOR(1, "Poor"),
    MODERATE(2, "Moderate"),
    GOOD(3, "Good"),
    GREAT(4, "Great");

    companion object {
        fun fromLevel(level: Int): SignalLevel = when (level.coerceIn(0, 4)) {
            4 -> GREAT
            3 -> GOOD
            2 -> MODERATE
            1 -> POOR
            else -> NONE_OR_UNKNOWN
        }
    }
}

/**
 * Pure Kotlin clock abstraction for deterministic unit testing of debounce and timestamps.
 */
fun interface Clock {
    fun nowMillis(): Long

    object System : Clock {
        override fun nowMillis(): Long = java.lang.System.currentTimeMillis()
    }
}
