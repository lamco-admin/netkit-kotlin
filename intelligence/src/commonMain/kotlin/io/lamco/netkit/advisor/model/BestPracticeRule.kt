package io.lamco.netkit.advisor.model

/**
 * WiFi best practice rule with rationale and applicability
 *
 * Represents expert knowledge about optimal WiFi network configuration,
 * security practices, performance optimization, and reliability improvements.
 *
 * Best practices are categorized by:
 * - **Security**: Authentication, encryption, access control
 * - **Performance**: Channel selection, bandwidth, QoS
 * - **Reliability**: Redundancy, coverage, roaming
 * - **Configuration**: General setup, vendor-specific settings
 * - **Compliance**: Industry standards, regulatory requirements
 *
 * Each rule has:
 * - **Severity**: MUST (critical), SHOULD (recommended), MAY (optional)
 * - **Source**: IEEE standard, vendor documentation, industry best practice
 * - **Applicability**: Network types, vendors, WiFi generations where rule applies
 *
 * Example Usage:
 * ```kotlin
 * val rule = BestPracticeRule(
 *     category = RuleCategory.SECURITY,
 *     title = "Use WPA3 for new deployments",
 *     description = "WPA3 provides enhanced security with SAE authentication",
 *     rationale = "WPA2 is vulnerable to KRACK attacks and dictionary attacks on weak passwords",
 *     severity = RuleSeverity.SHOULD,
 *     source = "WiFi Alliance WPA3 Specification",
 *     applicability = RuleApplicability(
 *         networkTypes = listOf(NetworkType.ENTERPRISE, NetworkType.SMALL_OFFICE),
 *         wifiGenerations = listOf(WifiGeneration.WIFI_6, WifiGeneration.WIFI_6E)
 *     )
 * )
 * ```
 *
 * @property category Rule category (security, performance, etc.)
 * @property title Short rule title
 * @property description Detailed rule description
 * @property rationale Why this rule matters
 * @property severity Rule importance (MUST, SHOULD, MAY)
 * @property source Authority or standard defining this rule
 * @property applicability When and where this rule applies
 *
 * @see RuleCategory
 * @see RuleSeverity
 * @see RuleApplicability
 */
data class BestPracticeRule(
    val category: RuleCategory,
    val title: String,
    val description: String,
    val rationale: String,
    val severity: RuleSeverity,
    val source: String,
    val applicability: RuleApplicability,
) {
    init {
        require(title.isNotBlank()) { "Rule title cannot be blank" }
        require(description.isNotBlank()) { "Rule description cannot be blank" }
        require(rationale.isNotBlank()) { "Rule rationale cannot be blank" }
        require(source.isNotBlank()) { "Rule source cannot be blank" }
    }

    /**
     * Check if rule applies to specific network context
     *
     * @param networkType Type of network (home, office, enterprise)
     * @param vendor Optional router vendor
     * @param wifiGeneration WiFi generation (WiFi 4/5/6/6E/7)
     * @return true if rule is applicable
     */
    fun appliesTo(
        networkType: NetworkType,
        vendor: RouterVendor? = null,
        wifiGeneration: WifiGeneration,
    ): Boolean {
        if (applicability.networkTypes.isNotEmpty() &&
            networkType !in applicability.networkTypes
        ) {
            return false
        }

        // Check vendor applicability (empty means all vendors)
        if (applicability.vendors.isNotEmpty() &&
            vendor != null &&
            vendor !in applicability.vendors
        ) {
            return false
        }

        if (applicability.wifiGenerations.isNotEmpty() &&
            wifiGeneration !in applicability.wifiGenerations
        ) {
            return false
        }

        return true
    }

    /**
     * Human-readable summary of the rule
     */
    val summary: String
        get() = "[$severity] $title - $description"
}

/**
 * Rule category classification
 */
enum class RuleCategory {
    /** Security-related rules (auth, encryption, access control) */
    SECURITY,

    /** Performance optimization rules (channel, bandwidth, QoS) */
    PERFORMANCE,

    /** Reliability rules (coverage, redundancy, roaming) */
    RELIABILITY,

    /** General configuration rules */
    CONFIGURATION,

    /** Compliance and regulatory rules */
    COMPLIANCE,

    ;

    /**
     * User-friendly display name
     */
    val displayName: String
        get() =
            when (this) {
                SECURITY -> "Security"
                PERFORMANCE -> "Performance"
                RELIABILITY -> "Reliability"
                CONFIGURATION -> "Configuration"
                COMPLIANCE -> "Compliance"
            }
}

/**
 * Rule severity levels
 *
 * Based on RFC 2119 keywords:
 * - MUST: Absolute requirement
 * - SHOULD: Strong recommendation (can be ignored with valid reason)
 * - MAY: Optional, discretionary
 */
enum class RuleSeverity {
    /** Absolute requirement - must be followed */
    MUST,

    /** Strong recommendation - should be followed unless good reason not to */
    SHOULD,

    /** Optional - may be followed for additional benefit */
    MAY,

    ;

    /**
     * User-friendly description
     */
    val description: String
        get() =
            when (this) {
                MUST -> "Required"
                SHOULD -> "Recommended"
                MAY -> "Optional"
            }

    /**
     * Numeric priority (higher = more important)
     */
    val priority: Int
        get() =
            when (this) {
                MUST -> 3
                SHOULD -> 2
                MAY -> 1
            }
}

/**
 * Rule applicability criteria
 *
 * Defines when and where a best practice rule applies.
 * Empty lists mean "applies to all".
 *
 * @property networkTypes Network types where rule applies (empty = all)
 * @property vendors Router vendors where rule applies (empty = all)
 * @property wifiGenerations WiFi generations where rule applies (empty = all)
 */
data class RuleApplicability(
    val networkTypes: List<NetworkType> = emptyList(),
    val vendors: List<RouterVendor> = emptyList(),
    val wifiGenerations: List<WifiGeneration> = emptyList(),
) {
    /**
     * Whether this rule applies universally (to all contexts)
     */
    val isUniversal: Boolean
        get() = networkTypes.isEmpty() && vendors.isEmpty() && wifiGenerations.isEmpty()
}

/**
 * Network type classification
 */
enum class NetworkType {
    /** Home network (1-10 devices, single AP) */
    HOME_BASIC,

    /** Advanced home network (10-50 devices, smart home, multiple APs) */
    HOME_ADVANCED,

    /** Small office (10-25 users, basic business needs) */
    SMALL_OFFICE,

    /** Medium business (25-100 users, multiple APs, VLANs) */
    MEDIUM_BUSINESS,

    /** Enterprise (100+ users, complex topology, managed infrastructure) */
    ENTERPRISE,

    /** Guest network (public WiFi, captive portal) */
    GUEST_NETWORK,

    /** High-density (conferences, stadiums, >100 concurrent users) */
    HIGH_DENSITY,

    /** Outdoor coverage */
    OUTDOOR,

    /** Mesh network (multiple APs, self-healing) */
    MESH_NETWORK,

    /** Healthcare facility (HIPAA compliance, medical devices) */
    HEALTHCARE,

    /** Education (schools, universities) */
    EDUCATION,

    /** Hospitality (hotels, resorts) */
    HOSPITALITY,

    /** Retail (stores, shopping centers) */
    RETAIL,

    /** Public venue (stadiums, conference centers) */
    PUBLIC_VENUE,

    ;

    /**
     * User-friendly display name
     */
    val displayName: String
        get() =
            when (this) {
                HOME_BASIC -> "Home (Basic)"
                HOME_ADVANCED -> "Home (Advanced)"
                SMALL_OFFICE -> "Small Office"
                MEDIUM_BUSINESS -> "Medium Business"
                ENTERPRISE -> "Enterprise"
                GUEST_NETWORK -> "Guest Network"
                HIGH_DENSITY -> "High-Density"
                OUTDOOR -> "Outdoor"
                MESH_NETWORK -> "Mesh Network"
                HEALTHCARE -> "Healthcare"
                EDUCATION -> "Education"
                HOSPITALITY -> "Hospitality"
                RETAIL -> "Retail"
                PUBLIC_VENUE -> "Public Venue"
            }
}

/**
 * Router vendor classification
 */
enum class RouterVendor {
    CISCO,
    ARUBA,
    UBIQUITI,
    RUCKUS,
    NETGEAR,
    TP_LINK,
    ASUS,
    LINKSYS,
    D_LINK,
    MIKROTIK,
    GENERIC,
    ;

    /**
     * Display name for the vendor
     */
    val displayName: String
        get() =
            when (this) {
                CISCO -> "Cisco"
                ARUBA -> "Aruba (HPE)"
                UBIQUITI -> "Ubiquiti"
                RUCKUS -> "Ruckus"
                NETGEAR -> "Netgear"
                TP_LINK -> "TP-Link"
                ASUS -> "ASUS"
                LINKSYS -> "Linksys"
                D_LINK -> "D-Link"
                MIKROTIK -> "MikroTik"
                GENERIC -> "Generic"
            }
}

/**
 * WiFi generation classification
 */
enum class WifiGeneration {
    WIFI_4, // 802.11n
    WIFI_5, // 802.11ac
    WIFI_6, // 802.11ax (2.4/5 GHz)
    WIFI_6E, // 802.11ax (6 GHz)
    WIFI_7, // 802.11be
    ;

    /**
     * Display name
     */
    val displayName: String
        get() =
            when (this) {
                WIFI_4 -> "WiFi 4 (802.11n)"
                WIFI_5 -> "WiFi 5 (802.11ac)"
                WIFI_6 -> "WiFi 6 (802.11ax)"
                WIFI_6E -> "WiFi 6E (6GHz)"
                WIFI_7 -> "WiFi 7 (802.11be)"
            }

    /**
     * IEEE standard name
     */
    val standardName: String
        get() =
            when (this) {
                WIFI_4 -> "802.11n"
                WIFI_5 -> "802.11ac"
                WIFI_6 -> "802.11ax"
                WIFI_6E -> "802.11ax (6GHz)"
                WIFI_7 -> "802.11be"
            }
}
