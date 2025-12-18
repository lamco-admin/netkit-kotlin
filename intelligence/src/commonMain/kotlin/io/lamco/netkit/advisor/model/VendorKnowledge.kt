package io.lamco.netkit.advisor.model

/**
 * Vendor-specific WiFi knowledge and best practices
 *
 * Contains vendor-specific recommendations, known issues, optimal settings,
 * and feature capabilities for different router manufacturers.
 *
 * This knowledge base helps provide vendor-specific advice that may differ
 * from generic best practices.
 *
 * Example Usage:
 * ```kotlin
 * val ciscoKnowledge = VendorKnowledge(
 *     vendor = RouterVendor.CISCO,
 *     bestPractices = listOf(
 *         "Enable RRM (Radio Resource Management) for automatic optimization",
 *         "Use FlexConnect for branch office deployments"
 *     ),
 *     knownIssues = listOf(
 *         VendorIssue(
 *             description = "ClientLink may cause issues with legacy devices",
 *             affectedModels = listOf("Aironet 2800", "Aironet 3800"),
 *             workaround = "Disable ClientLink for 2.4GHz if experiencing issues",
 *             severity = IssueSeverity.MEDIUM
 *         )
 *     ),
 *     optimalSettings = mapOf(
 *         "DCA Interval" to "600 seconds",
 *         "TPC Threshold" to "-70 dBm",
 *         "Coverage Hole Detection" to "Enable"
 *     ),
 *     features = listOf(
 *         VendorFeature("CleanAir", "Spectrum intelligence and interference mitigation"),
 *         VendorFeature("ClientLink", "Beamforming for legacy devices")
 *     )
 * )
 * ```
 *
 * @property vendor Router vendor
 * @property bestPractices List of vendor-specific best practices
 * @property knownIssues Known issues and workarounds
 * @property optimalSettings Recommended settings (key-value pairs)
 * @property features Vendor-specific features and their descriptions
 *
 * @see VendorIssue
 * @see VendorFeature
 */
data class VendorKnowledge(
    val vendor: RouterVendor,
    val bestPractices: List<String>,
    val knownIssues: List<VendorIssue> = emptyList(),
    val optimalSettings: Map<String, String> = emptyMap(),
    val features: List<VendorFeature> = emptyList()
) {
    init {
        require(bestPractices.isNotEmpty()) {
            "Vendor knowledge must include at least one best practice"
        }
    }

    /**
     * Get critical issues that should be addressed immediately
     */
    val criticalIssues: List<VendorIssue>
        get() = knownIssues.filter { it.severity == IssueSeverity.CRITICAL }

    /**
     * Get all issues sorted by severity (critical first)
     */
    val issuesBySeverity: List<VendorIssue>
        get() = knownIssues.sortedByDescending { it.severity.priority }
}

/**
 * Vendor-specific issue with workaround
 *
 * @property description Issue description
 * @property affectedModels List of affected model numbers
 * @property workaround Recommended workaround or solution
 * @property severity Issue severity
 * @property affectedFirmware Firmware versions affected (empty = all versions)
 */
data class VendorIssue(
    val description: String,
    val affectedModels: List<String> = emptyList(),
    val workaround: String,
    val severity: IssueSeverity,
    val affectedFirmware: List<String> = emptyList()
) {
    init {
        require(description.isNotBlank()) { "Issue description cannot be blank" }
        require(workaround.isNotBlank()) { "Workaround cannot be blank" }
    }

    /**
     * Whether this issue affects all models
     */
    val affectsAllModels: Boolean
        get() = affectedModels.isEmpty()

    /**
     * Human-readable summary
     */
    val summary: String
        get() = buildString {
            append("[$severity] $description")
            if (affectedModels.isNotEmpty()) {
                append(" (${affectedModels.joinToString(", ")})")
            }
        }
}

/**
 * Issue severity classification
 */
enum class IssueSeverity {
    /** Informational - minor inconvenience */
    INFO,

    /** Low severity - affects non-critical functionality */
    LOW,

    /** Medium severity - noticeable performance impact */
    MEDIUM,

    /** High severity - significant functionality impact */
    HIGH,

    /** Critical - causes failures or security vulnerabilities */
    CRITICAL;

    /**
     * User-friendly description
     */
    val description: String
        get() = when (this) {
            INFO -> "Informational"
            LOW -> "Low"
            MEDIUM -> "Medium"
            HIGH -> "High"
            CRITICAL -> "Critical"
        }

    /**
     * Numeric priority for sorting (higher = more severe)
     */
    val priority: Int
        get() = ordinal
}

/**
 * Vendor-specific feature
 *
 * @property name Feature name
 * @property description Feature description
 * @property requiresLicense Whether feature requires additional license
 * @property minimumFirmware Minimum firmware version required (null = all versions)
 */
data class VendorFeature(
    val name: String,
    val description: String,
    val requiresLicense: Boolean = false,
    val minimumFirmware: String? = null
) {
    init {
        require(name.isNotBlank()) { "Feature name cannot be blank" }
        require(description.isNotBlank()) { "Feature description cannot be blank" }
    }

    /**
     * Summary of the feature
     */
    val summary: String
        get() = buildString {
            append(name)
            if (requiresLicense) {
                append(" (License Required)")
            }
            append(": $description")
        }
}

/**
 * Standards reference for knowledge base
 *
 * References to IEEE, WiFi Alliance, and other standards that define
 * WiFi behavior and best practices.
 *
 * @property id Standard identifier (e.g., "IEEE 802.11-2020")
 * @property name Standard name
 * @property description Brief description
 * @property url Reference URL (optional)
 * @property category Standard category
 */
data class StandardsReference(
    val id: String,
    val name: String,
    val description: String,
    val url: String? = null,
    val category: StandardCategory
) {
    init {
        require(id.isNotBlank()) { "Standard ID cannot be blank" }
        require(name.isNotBlank()) { "Standard name cannot be blank" }
        require(description.isNotBlank()) { "Standard description cannot be blank" }
    }

    /**
     * Summary of the standard
     */
    val summary: String
        get() = "$id - $name: $description"
}

/**
 * Standards category classification
 */
enum class StandardCategory {
    /** IEEE 802.11 WiFi standards */
    IEEE_WIFI,

    /** WiFi Alliance certifications (WPA3, WiFi 6, etc.) */
    WIFI_ALLIANCE,

    /** Security standards (802.1X, EAP, etc.) */
    SECURITY,

    /** Quality of Service standards */
    QOS,

    /** Regulatory standards (FCC, ETSI, etc.) */
    REGULATORY,

    /** Industry best practices (PCI-DSS, HIPAA, etc.) */
    COMPLIANCE;

    /**
     * Display name
     */
    val displayName: String
        get() = when (this) {
            IEEE_WIFI -> "IEEE WiFi Standards"
            WIFI_ALLIANCE -> "WiFi Alliance"
            SECURITY -> "Security Standards"
            QOS -> "Quality of Service"
            REGULATORY -> "Regulatory"
            COMPLIANCE -> "Compliance Standards"
        }
}
