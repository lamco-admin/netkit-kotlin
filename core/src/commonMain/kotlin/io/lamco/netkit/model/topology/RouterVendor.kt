package io.lamco.netkit.model.topology

/**
 * WiFi router manufacturers and vendors
 *
 * Identified through:
 * - OUI (Organizationally Unique Identifier) lookup from BSSID
 * - WPS vendor information elements
 * - Vendor-specific information elements
 *
 * @property displayName Human-readable vendor name
 * @property reliability How reliably this vendor can be detected (0.0-1.0)
 */
enum class RouterVendor(
    val displayName: String,
    val reliability: Double,
) {
    /**
     * Cisco Systems (enterprise and consumer equipment)
     * - Often used in enterprise deployments
     * - Reliable mesh and roaming support
     */
    CISCO("Cisco Systems", 0.95),

    /**
     * Ubiquiti Networks (UniFi, EdgeRouter, AmpliFi)
     * - Popular for prosumer/enterprise
     * - Excellent multi-AP support
     */
    UBIQUITI("Ubiquiti Networks", 0.95),

    /**
     * Aruba Networks (HPE subsidiary)
     * - Enterprise-grade APs
     * - Advanced roaming features
     */
    ARUBA("Aruba Networks", 0.90),

    /**
     * Ruckus Wireless (CommScope)
     * - High-performance enterprise APs
     * - BeamFlex technology
     */
    RUCKUS("Ruckus Wireless", 0.90),

    /**
     * Netgear (consumer and SMB equipment)
     * - Popular home routers and mesh systems (Orbi)
     */
    NETGEAR("Netgear", 0.85),

    /**
     * TP-Link Technologies
     * - Consumer routers, mesh systems (Deco, Omada)
     */
    TP_LINK("TP-Link", 0.85),

    /**
     * ASUS (consumer routers and AiMesh)
     * - Gaming routers, AiMesh technology
     */
    ASUS("ASUS", 0.85),

    /**
     * Linksys (Belkin subsidiary)
     * - Consumer routers and mesh systems (Velop)
     */
    LINKSYS("Linksys", 0.85),

    /**
     * Google (Google WiFi, Nest WiFi)
     * - Mesh-focused consumer products
     */
    GOOGLE("Google", 0.90),

    /**
     * Amazon (eero mesh systems)
     * - Consumer mesh WiFi
     */
    AMAZON_EERO("Amazon eero", 0.90),

    /**
     * Apple (AirPort - discontinued but still in use)
     * - Legacy AirPort Express/Extreme/Time Capsule
     */
    APPLE("Apple", 0.95),

    /**
     * Motorola (consumer routers)
     */
    MOTOROLA("Motorola", 0.80),

    /**
     * D-Link Corporation
     */
    D_LINK("D-Link", 0.80),

    /**
     * Belkin International
     */
    BELKIN("Belkin", 0.80),

    /**
     * Qualcomm (reference designs, chipsets)
     */
    QUALCOMM("Qualcomm", 0.75),

    /**
     * Broadcom (chipsets, reference designs)
     */
    BROADCOM("Broadcom", 0.75),

    /**
     * Intel (WiFi adapters and reference APs)
     */
    INTEL("Intel", 0.85),

    /**
     * Huawei Technologies
     */
    HUAWEI("Huawei", 0.85),

    /**
     * Xiaomi (Mi Router series)
     */
    XIAOMI("Xiaomi", 0.80),

    /**
     * Samsung Electronics
     */
    SAMSUNG("Samsung", 0.80),

    /**
     * Arris/CommScope (cable modem/router combos)
     */
    ARRIS("Arris", 0.80),

    /**
     * Zyxel Communications
     */
    ZYXEL("Zyxel", 0.80),

    /**
     * Unknown or unidentified vendor
     */
    UNKNOWN("Unknown Vendor", 0.0),
    ;

    /**
     * Whether this vendor typically supports multi-AP deployments
     */
    val supportsMultiAp: Boolean
        get() =
            when (this) {
                CISCO, UBIQUITI, ARUBA, RUCKUS, GOOGLE, AMAZON_EERO -> true
                NETGEAR, TP_LINK, ASUS, LINKSYS -> true // Modern mesh systems
                else -> false
            }

    /**
     * Whether this vendor typically supports fast roaming (802.11k/r/v)
     */
    val supportsFastRoaming: Boolean
        get() =
            when (this) {
                CISCO, UBIQUITI, ARUBA, RUCKUS -> true // Enterprise vendors
                GOOGLE, AMAZON_EERO -> true // Mesh-focused
                NETGEAR, TP_LINK, ASUS -> true // Modern consumer with mesh
                else -> false
            }

    /**
     * Typical deployment type for this vendor
     */
    val deploymentType: DeploymentType
        get() =
            when (this) {
                CISCO, UBIQUITI, ARUBA, RUCKUS -> DeploymentType.ENTERPRISE
                GOOGLE, AMAZON_EERO, NETGEAR, TP_LINK, ASUS, LINKSYS -> DeploymentType.CONSUMER_MESH
                APPLE, MOTOROLA, D_LINK, BELKIN -> DeploymentType.CONSUMER_SINGLE
                else -> DeploymentType.UNKNOWN
            }

    companion object {
        /**
         * Determine vendor from OUI (first 3 bytes of MAC address)
         *
         * @param oui OUI string (e.g., "00:1A:2B" or "001A2B")
         * @return Corresponding RouterVendor or UNKNOWN
         */
        fun fromOui(oui: String): RouterVendor {
            // Normalize OUI (remove colons, uppercase)
            val normalizedOui = oui.replace(":", "").uppercase()

            // Common OUI prefixes (examples - real implementation would have comprehensive database)
            return when {
                normalizedOui.startsWith("001D") -> CISCO
                normalizedOui.startsWith("F09F") -> UBIQUITI
                normalizedOui.startsWith("00090F") -> ARUBA
                normalizedOui.startsWith("001217") -> RUCKUS
                normalizedOui.startsWith("1062EB") -> NETGEAR
                normalizedOui.startsWith("F4F26D") -> GOOGLE
                normalizedOui.startsWith("F00272") -> AMAZON_EERO
                normalizedOui.startsWith("001451") -> APPLE
                else -> UNKNOWN
            }
        }

        /**
         * Determine vendor from WPS device name or manufacturer string
         *
         * @param deviceName WPS device name
         * @param manufacturer WPS manufacturer string
         * @return Corresponding RouterVendor or UNKNOWN
         */
        fun fromWpsInfo(
            deviceName: String?,
            manufacturer: String?,
        ): RouterVendor {
            val searchText = "${deviceName ?: ""} ${manufacturer ?: ""}".uppercase()
            return findVendorByWpsPattern(searchText)
        }

        /**
         * Find vendor by matching WPS pattern keywords.
         */
        private fun findVendorByWpsPattern(searchText: String): RouterVendor {
            val match =
                WPS_VENDOR_PATTERNS.find { (_, keywords) ->
                    keywords.any { keyword -> keyword in searchText }
                }
            return match?.first ?: UNKNOWN
        }

        /**
         * Vendor detection patterns for WPS device names/manufacturers.
         * Ordered by specificity (more specific patterns first).
         */
        private val WPS_VENDOR_PATTERNS =
            listOf(
                CISCO to listOf("CISCO"),
                UBIQUITI to listOf("UBIQUITI", "UNIFI"),
                ARUBA to listOf("ARUBA"),
                RUCKUS to listOf("RUCKUS"),
                NETGEAR to listOf("NETGEAR", "ORBI"),
                TP_LINK to listOf("TP-LINK", "DECO"),
                ASUS to listOf("ASUS", "AIMESH"),
                LINKSYS to listOf("LINKSYS", "VELOP"),
                GOOGLE to listOf("GOOGLE", "NEST WIFI"),
                AMAZON_EERO to listOf("EERO"),
                APPLE to listOf("APPLE", "AIRPORT"),
            )
    }
}

/**
 * Type of WiFi network deployment
 */
enum class DeploymentType {
    /** Enterprise/commercial deployment with multiple managed APs */
    ENTERPRISE,

    /** Consumer mesh system (multiple APs, coordinated) */
    CONSUMER_MESH,

    /** Single consumer router/AP */
    CONSUMER_SINGLE,

    /** Unknown deployment type */
    UNKNOWN,
}
