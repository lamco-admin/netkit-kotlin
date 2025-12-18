package io.lamco.netkit.security.model

/**
 * WPS configuration information extracted from beacon/probe response
 *
 * WPS was designed to simplify WiFi setup but introduced serious security vulnerabilities,
 * particularly the PIN method which can be brute-forced in hours.
 *
 * Background:
 * - WPS PIN has only 10^4 possible values due to checksum validation
 * - Vulnerable to Reaver brute force attacks (4-10 hours typical)
 * - Vulnerable to Pixie Dust attacks (offline PIN recovery in seconds)
 * - Many routers never properly disable WPS even when "disabled" in UI
 * - WPS push-button (PBC) is safer but still has timing attack risks
 *
 * References: Wi-Fi Alliance WPS specification, CVE-2011-5053,
 * Viehböck (2011) "Brute forcing Wi-Fi Protected Setup",
 * Pixie Dust attack (Dominique Bongard, 2014)
 *
 * @property configMethods Set of supported configuration methods
 * @property wpsState WPS setup state
 * @property locked Whether WPS is locked (disabled after failed attempts)
 * @property deviceName Device name advertised via WPS
 * @property manufacturer Manufacturer name from WPS IE
 * @property modelName Model name from WPS IE
 * @property version WPS version (1.0, 2.0, etc.)
 */
data class WpsInfo(
    val configMethods: Set<WpsConfigMethod>,
    val wpsState: WpsState,
    val locked: Boolean?,
    val deviceName: String?,
    val manufacturer: String?,
    val modelName: String?,
    val version: String?,
) {
    /**
     * Whether PIN method is supported
     */
    val supportsPinMethod: Boolean
        get() = configMethods.any { it.usesPin }

    /**
     * Whether only push-button is supported (safer)
     */
    val isPushButtonOnly: Boolean
        get() =
            configMethods.isNotEmpty() &&
                configMethods.all { it == WpsConfigMethod.PUSH_BUTTON } &&
                !supportsPinMethod

    /**
     * Whether WPS appears to be disabled or absent
     */
    val isDisabled: Boolean
        get() = wpsState == WpsState.NOT_CONFIGURED && configMethods.isEmpty()

    /**
     * Whether WPS appears to be actively configured
     */
    val isConfigured: Boolean
        get() = wpsState == WpsState.CONFIGURED

    /**
     * Whether WPS is locked (good - reduces attack surface)
     */
    val isLocked: Boolean
        get() = locked == true

    /**
     * Human-readable WPS status summary
     */
    val statusSummary: String
        get() =
            when {
                isDisabled -> "Disabled"
                isLocked -> "Locked"
                isPushButtonOnly -> "Push-button only"
                supportsPinMethod && isConfigured -> "Active with PIN (vulnerable)"
                supportsPinMethod -> "PIN method available (risky)"
                else -> "Active"
            }
}

/**
 * WPS configuration methods
 *
 * Multiple methods may be advertised simultaneously.
 */
enum class WpsConfigMethod(
    val displayName: String,
    val usesPin: Boolean,
) {
    /** USB method (rarely used) */
    USB("USB", false),

    /** Ethernet method (rarely used) */
    ETHERNET("Ethernet", false),

    /** Label method - PIN printed on device label */
    LABEL("Label (PIN)", true),

    /** Display method - PIN shown on device screen */
    DISPLAY("Display (PIN)", true),

    /** External NFC token */
    EXTERNAL_NFC("External NFC", false),

    /** Integrated NFC token */
    INTEGRATED_NFC("Integrated NFC", false),

    /** NFC interface */
    NFC_INTERFACE("NFC Interface", false),

    /** Push-button configuration (physical or virtual button) */
    PUSH_BUTTON("Push Button", false),

    /** Keypad - user enters PIN on device */
    KEYPAD("Keypad (PIN)", true),

    /** Virtual push-button (software button in UI) */
    VIRTUAL_PUSH_BUTTON("Virtual Push Button", false),

    /** Physical push-button (actual button on device) */
    PHYSICAL_PUSH_BUTTON("Physical Push Button", false),

    /** Virtual display - PIN shown in software UI */
    VIRTUAL_DISPLAY("Virtual Display (PIN)", true),

    /** Physical display - PIN shown on hardware display */
    PHYSICAL_DISPLAY("Physical Display (PIN)", true),
    ;

    companion object {
        /**
         * Parse config methods from WPS configuration methods bitmask
         *
         * @param bitmask 16-bit bitmask from WPS IE
         * @return Set of config methods
         */
        fun fromBitmask(bitmask: Int): Set<WpsConfigMethod> {
            val methods = mutableSetOf<WpsConfigMethod>()

            if (bitmask and 0x0001 != 0) methods.add(USB)
            if (bitmask and 0x0002 != 0) methods.add(ETHERNET)
            if (bitmask and 0x0004 != 0) methods.add(LABEL)
            if (bitmask and 0x0008 != 0) methods.add(DISPLAY)
            if (bitmask and 0x0010 != 0) methods.add(EXTERNAL_NFC)
            if (bitmask and 0x0020 != 0) methods.add(INTEGRATED_NFC)
            if (bitmask and 0x0040 != 0) methods.add(NFC_INTERFACE)
            if (bitmask and 0x0080 != 0) methods.add(PUSH_BUTTON)
            if (bitmask and 0x0100 != 0) methods.add(KEYPAD)
            if (bitmask and 0x0200 != 0) methods.add(VIRTUAL_PUSH_BUTTON)
            if (bitmask and 0x0400 != 0) methods.add(PHYSICAL_PUSH_BUTTON)
            if (bitmask and 0x2000 != 0) methods.add(VIRTUAL_DISPLAY)
            if (bitmask and 0x4000 != 0) methods.add(PHYSICAL_DISPLAY)

            return methods
        }
    }
}

/**
 * WPS setup state
 */
enum class WpsState(
    val displayName: String,
) {
    /** WPS not configured */
    NOT_CONFIGURED("Not Configured"),

    /** WPS configured and ready */
    CONFIGURED("Configured"),
    ;

    companion object {
        /**
         * Parse WPS state from state value
         *
         * @param value State value from WPS IE (1 = not configured, 2 = configured)
         * @return WpsState
         */
        fun fromValue(value: Int): WpsState =
            when (value) {
                1 -> NOT_CONFIGURED
                2 -> CONFIGURED
                else -> NOT_CONFIGURED
            }
    }
}

/**
 * WPS risk assessment result
 *
 * Provides risk scoring and specific vulnerability identification.
 *
 * Risk score scale:
 * - 0.0-0.2: Low risk (WPS disabled or push-button only, locked)
 * - 0.3-0.5: Medium risk (Push-button enabled, not locked)
 * - 0.6-0.8: High risk (PIN supported but locked)
 * - 0.9-1.0: Critical risk (PIN supported, unlocked, configured)
 *
 * @property bssid Access point BSSID
 * @property riskScore Risk score 0.0-1.0
 * @property issues Specific WPS vulnerabilities detected
 * @property wpsInfo WPS configuration details
 */
data class WpsRiskScore(
    val bssid: String,
    val riskScore: Double,
    val issues: List<WpsIssue>,
    val wpsInfo: WpsInfo?,
) {
    init {
        require(bssid.isNotBlank()) { "BSSID must not be blank" }
        require(riskScore in 0.0..1.0) {
            "Risk score must be 0.0-1.0, got $riskScore"
        }
    }

    /**
     * Risk level category
     */
    val riskLevel: RiskLevel
        get() =
            when {
                riskScore >= 0.9 -> RiskLevel.CRITICAL
                riskScore >= 0.6 -> RiskLevel.HIGH
                riskScore >= 0.3 -> RiskLevel.MEDIUM
                else -> RiskLevel.LOW
            }

    /**
     * Whether WPS poses a critical security risk
     */
    val isCriticalRisk: Boolean
        get() = riskLevel == RiskLevel.CRITICAL

    /**
     * Whether WPS poses any significant risk
     */
    val hasSignificantRisk: Boolean
        get() = riskScore >= 0.5

    /**
     * Human-readable risk summary
     */
    val riskSummary: String
        get() =
            buildString {
                append("WPS Risk: $riskLevel")
                if (wpsInfo != null) {
                    append(" - ${wpsInfo.statusSummary}")
                }
                if (issues.isNotEmpty()) {
                    append(" (${issues.size} issue(s))")
                }
            }

    companion object {
        /**
         * Calculate WPS risk score from WPS configuration
         *
         * Algorithm:
         * - PIN supported & unlocked & configured: 1.0 (CRITICAL)
         * - PIN supported & unlocked & not configured: 0.8 (HIGH)
         * - PIN supported & locked: 0.6 (MEDIUM-HIGH)
         * - Push-button only, configured, unlocked: 0.4 (MEDIUM)
         * - Push-button only, locked or disabled: 0.2 (LOW)
         * - WPS disabled/absent: 0.0 (NO RISK)
         *
         * @param wpsInfo WPS configuration information
         * @return Risk score 0.0-1.0
         */
        fun calculateRiskScore(wpsInfo: WpsInfo?): Double {
            if (wpsInfo == null || wpsInfo.isDisabled) return 0.0

            val pinSupported = wpsInfo.supportsPinMethod
            val isLocked = wpsInfo.isLocked
            val isConfigured = wpsInfo.isConfigured

            return when {
                // PIN method available - most dangerous
                pinSupported && !isLocked && isConfigured -> 1.0 // CRITICAL
                pinSupported && !isLocked -> 0.8 // HIGH
                pinSupported && isLocked -> 0.6 // MEDIUM-HIGH

                // Push-button only - lower risk but not zero
                wpsInfo.isPushButtonOnly && !isLocked && isConfigured -> 0.4 // MEDIUM
                wpsInfo.isPushButtonOnly && !isLocked -> 0.3 // MEDIUM-LOW
                wpsInfo.isPushButtonOnly && isLocked -> 0.2 // LOW

                // Other configurations
                isConfigured && !isLocked -> 0.5 // MEDIUM
                isLocked -> 0.2 // LOW
                else -> 0.3 // MEDIUM-LOW
            }
        }

        /**
         * Detect WPS security issues
         *
         * @param wpsInfo WPS configuration information
         * @return List of detected issues
         */
        fun detectIssues(wpsInfo: WpsInfo?): List<WpsIssue> {
            if (wpsInfo == null || wpsInfo.isDisabled) return emptyList()

            val issues = mutableListOf<WpsIssue>()

            // Check for PIN support
            if (wpsInfo.supportsPinMethod) {
                issues.add(WpsIssue.PinSupported)

                // Check if unlocked
                if (!wpsInfo.isLocked) {
                    issues.add(WpsIssue.UnlockedState)
                }
            }

            // Check if configured but still active
            if (wpsInfo.isConfigured && !wpsInfo.isLocked) {
                issues.add(WpsIssue.ConfiguredButStillActive)
            }

            // Check for unknown/risky state
            if (wpsInfo.locked == null && wpsInfo.supportsPinMethod) {
                issues.add(WpsIssue.UnknownLockState)
            }

            return issues
        }

        /**
         * Create WPS risk score from WPS information
         *
         * @param bssid Access point BSSID
         * @param wpsInfo WPS configuration (null if WPS not present)
         * @return WpsRiskScore
         */
        fun fromWpsInfo(
            bssid: String,
            wpsInfo: WpsInfo?,
        ): WpsRiskScore {
            val riskScore = calculateRiskScore(wpsInfo)
            val issues = detectIssues(wpsInfo)

            return WpsRiskScore(
                bssid = bssid,
                riskScore = riskScore,
                issues = issues,
                wpsInfo = wpsInfo,
            )
        }
    }
}

/**
 * Specific WPS security issues
 */
sealed class WpsIssue {
    /**
     * Short description of the issue
     */
    abstract val description: String

    /**
     * PIN method is supported (vulnerable to brute force)
     */
    object PinSupported : WpsIssue() {
        override val description = "WPS PIN method supported (vulnerable to brute force)"
    }

    /**
     * WPS is not locked (allows unlimited PIN attempts)
     */
    object UnlockedState : WpsIssue() {
        override val description = "WPS unlocked (allows brute force attacks)"
    }

    /**
     * WPS is configured but still active (should be disabled after setup)
     */
    object ConfiguredButStillActive : WpsIssue() {
        override val description = "WPS is configured but still active (unnecessary risk)"
    }

    /**
     * WPS lock state is unknown (unable to determine security)
     */
    object UnknownLockState : WpsIssue() {
        override val description = "WPS lock state unknown (potential risk)"
    }
}

/**
 * Risk level categories
 */
enum class RiskLevel {
    /** No significant risk */
    LOW,

    /** Moderate risk - some exposure */
    MEDIUM,

    /** High risk - serious vulnerability */
    HIGH,

    /** Critical risk - immediate threat */
    CRITICAL,

    ;

    /**
     * User-friendly description
     */
    val description: String
        get() =
            when (this) {
                LOW -> "Low Risk"
                MEDIUM -> "Medium Risk"
                HIGH -> "High Risk - Action Recommended"
                CRITICAL -> "Critical Risk - Immediate Action Required"
            }
}
