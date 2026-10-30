package com.example.domain

/**
 * Pure Kotlin mapper that translates Android TelephonyManager networkType and TelephonyDisplayInfo
 * overrideNetworkType constants into an honest [NetworkGeneration].
 *
 * All constants mirror android.telephony.TelephonyManager and android.telephony.TelephonyDisplayInfo
 * integer values directly so this class has zero Android framework runtime dependencies and can be
 * exhaustively unit-tested on any JVM.
 */
object NetworkGenerationMapper {

    // TelephonyManager.NETWORK_TYPE_* constants
    const val NETWORK_TYPE_UNKNOWN = 0
    const val NETWORK_TYPE_GPRS = 1
    const val NETWORK_TYPE_EDGE = 2
    const val NETWORK_TYPE_UMTS = 3
    const val NETWORK_TYPE_CDMA = 4
    const val NETWORK_TYPE_EVDO_0 = 5
    const val NETWORK_TYPE_EVDO_A = 6
    const val NETWORK_TYPE_1xRTT = 7
    const val NETWORK_TYPE_HSDPA = 8
    const val NETWORK_TYPE_HSUPA = 9
    const val NETWORK_TYPE_HSPA = 10
    const val NETWORK_TYPE_IDEN = 11
    const val NETWORK_TYPE_EVDO_B = 12
    const val NETWORK_TYPE_LTE = 13
    const val NETWORK_TYPE_EHRPD = 14
    const val NETWORK_TYPE_HSPAP = 15
    const val NETWORK_TYPE_GSM = 16
    const val NETWORK_TYPE_TD_SCDMA = 17
    const val NETWORK_TYPE_IWLAN = 18
    const val NETWORK_TYPE_LTE_CA = 19 // Hidden/system constant on some OEMs
    const val NETWORK_TYPE_NR = 20

    // TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_* constants
    const val OVERRIDE_NETWORK_TYPE_NONE = 0
    const val OVERRIDE_NETWORK_TYPE_LTE_CA = 1
    const val OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO = 2
    const val OVERRIDE_NETWORK_TYPE_NR_NSA = 3
    const val OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE = 4 // Deprecated alias for NR_ADVANCED (5)
    const val OVERRIDE_NETWORK_TYPE_NR_ADVANCED = 5

    /**
     * Maps the current cellular telemetry to [NetworkGeneration].
     *
     * Rules (Accuracy First):
     * 1. If permission is denied, airplane mode is ON, SIM is absent, or service is out-of-service,
     *    immediately return [NetworkGeneration.UNKNOWN]. Never fabricate readings.
     * 2. Map to 5G when [overrideNetworkType] is [OVERRIDE_NETWORK_TYPE_NR_NSA],
     *    [OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE], or [OVERRIDE_NETWORK_TYPE_NR_ADVANCED],
     *    OR when [networkType] is [NETWORK_TYPE_NR].
     * 3. Map LTE ([NETWORK_TYPE_LTE], [NETWORK_TYPE_LTE_CA]) or LTE overrides
     *    ([OVERRIDE_NETWORK_TYPE_LTE_CA], [OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO]) to 4G.
     * 4. Map UMTS/HSPA/EVDO/EHRPD/TD-SCDMA variants to 3G.
     * 5. Map GPRS/EDGE/GSM/CDMA/1xRTT/IDEN to 2G.
     * 6. Everything else (including IWLAN Wi-Fi calling or NETWORK_TYPE_UNKNOWN) maps to [NetworkGeneration.UNKNOWN].
     */
    fun map(
        networkType: Int,
        overrideNetworkType: Int,
        hasPermission: Boolean = true,
        isAirplaneModeOn: Boolean = false,
        hasActiveSim: Boolean = true,
        isInService: Boolean = true
    ): NetworkGeneration {
        if (!hasPermission || isAirplaneModeOn || !hasActiveSim || !isInService) {
            return NetworkGeneration.UNKNOWN
        }

        // Check 5G override first (5G NSA / 5G Advanced / mmWave anchored on LTE)
        when (overrideNetworkType) {
            OVERRIDE_NETWORK_TYPE_NR_NSA,
            OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE,
            OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> {
                // Only valid 5G NSA if underlying network is LTE or NR (not out-of-service/unknown)
                if (networkType != NETWORK_TYPE_UNKNOWN && networkType != NETWORK_TYPE_IWLAN) {
                    return NetworkGeneration.FIVE_G
                }
            }
        }

        // Check primary base networkType
        return when (networkType) {
            NETWORK_TYPE_NR -> NetworkGeneration.FIVE_G

            NETWORK_TYPE_LTE,
            NETWORK_TYPE_LTE_CA -> NetworkGeneration.FOUR_G

            NETWORK_TYPE_UMTS,
            NETWORK_TYPE_EVDO_0,
            NETWORK_TYPE_EVDO_A,
            NETWORK_TYPE_HSDPA,
            NETWORK_TYPE_HSUPA,
            NETWORK_TYPE_HSPA,
            NETWORK_TYPE_EVDO_B,
            NETWORK_TYPE_EHRPD,
            NETWORK_TYPE_HSPAP,
            NETWORK_TYPE_TD_SCDMA -> NetworkGeneration.THREE_G

            NETWORK_TYPE_GPRS,
            NETWORK_TYPE_EDGE,
            NETWORK_TYPE_CDMA,
            NETWORK_TYPE_1xRTT,
            NETWORK_TYPE_IDEN,
            NETWORK_TYPE_GSM -> NetworkGeneration.TWO_G

            else -> {
                // If override says LTE_CA or LTE_ADVANCED_PRO while networkType was OEM-masked
                if (overrideNetworkType == OVERRIDE_NETWORK_TYPE_LTE_CA ||
                    overrideNetworkType == OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO
                ) {
                    NetworkGeneration.FOUR_G
                } else {
                    NetworkGeneration.UNKNOWN
                }
            }
        }
    }
}
