package io.lamco.netkit.advisor.knowledge

import io.lamco.netkit.advisor.model.StandardCategory
import io.lamco.netkit.advisor.model.StandardsReference

/**
 * Database of WiFi standards and specifications
 *
 * Provides reference information about IEEE 802.11 standards, WiFi Alliance
 * certifications, security protocols, and compliance requirements.
 *
 * This database helps:
 * - Explain technical terminology to users
 * - Provide authoritative references for recommendations
 * - Link to official specifications
 * - Track which standards apply to different scenarios
 *
 * Example Usage:
 * ```kotlin
 * val standards = StandardsDatabase.create()
 *
 * // Get IEEE WiFi standards
 * val wifiStandards = standards.getStandardsByCategory(StandardCategory.IEEE_WIFI)
 *
 * // Look up specific standard
 * val wpa3 = standards.getStandard("WPA3")
 *
 * // Get security-related standards
 * val securityStandards = standards.getStandardsByCategory(StandardCategory.SECURITY)
 * ```
 *
 * @property standards Collection of standards references
 */
class StandardsDatabase(
    private val standards: List<StandardsReference>,
) {
    init {
        require(standards.isNotEmpty()) { "Standards database cannot be empty" }
    }

    /**
     * Get all standards in a category
     *
     * @param category Standard category (IEEE, Security, etc.)
     * @return List of standards in category
     */
    fun getStandardsByCategory(category: StandardCategory): List<StandardsReference> = standards.filter { it.category == category }

    /**
     * Get standard by ID
     *
     * @param id Standard identifier (e.g., "IEEE 802.11ax")
     * @return Standard reference, or null if not found
     */
    fun getStandard(id: String): StandardsReference? = standards.find { it.id.equals(id, ignoreCase = true) }

    /**
     * Search standards by keyword
     *
     * Searches in ID, name, and description.
     *
     * @param keyword Search term
     * @return List of matching standards
     */
    fun search(keyword: String): List<StandardsReference> =
        standards.filter {
            it.id.contains(keyword, ignoreCase = true) ||
                it.name.contains(keyword, ignoreCase = true) ||
                it.description.contains(keyword, ignoreCase = true)
        }

    /**
     * Get all IEEE 802.11 WiFi standards
     */
    fun getWiFiStandards(): List<StandardsReference> = getStandardsByCategory(StandardCategory.IEEE_WIFI)

    /**
     * Get all security-related standards
     */
    fun getSecurityStandards(): List<StandardsReference> = getStandardsByCategory(StandardCategory.SECURITY)

    /**
     * Get compliance standards
     */
    fun getComplianceStandards(): List<StandardsReference> = getStandardsByCategory(StandardCategory.COMPLIANCE)

    /**
     * Get summary statistics
     */
    fun getSummary(): StandardsDatabaseSummary =
        StandardsDatabaseSummary(
            totalStandards = standards.size,
            byCategory =
                standards
                    .groupBy { it.category }
                    .mapValues { it.value.size },
            withUrls = standards.count { it.url != null },
        )

    companion object {
        /**
         * Create standards database with default references
         *
         * @return Standards database with comprehensive reference collection
         */
        fun create(): StandardsDatabase = StandardsDatabase(DEFAULT_STANDARDS)
    }
}

/**
 * Standards database summary statistics
 *
 * @property totalStandards Total number of standards
 * @property byCategory Count of standards per category
 * @property withUrls Number of standards with reference URLs
 */
data class StandardsDatabaseSummary(
    val totalStandards: Int,
    val byCategory: Map<StandardCategory, Int>,
    val withUrls: Int,
)

// ========================================
// Default Standards Database
// ========================================

/**
 * Default standards references
 */
private val DEFAULT_STANDARDS =
    listOf(
        // IEEE 802.11 WiFi Standards
        StandardsReference(
            id = "IEEE 802.11-2020",
            name =
                "IEEE Standard for Information Technology—Telecommunications and information exchange between " +
                    "systems Local and metropolitan area networks—Specific requirements - Part 11: Wireless LAN " +
                    "Medium Access Control (MAC) and Physical Layer (PHY) Specifications",
            description = "The comprehensive WiFi standard that incorporates 802.11a/b/g/n/ac and defines MAC and PHY layers",
            url = "https://standards.ieee.org/standard/802_11-2020.html",
            category = StandardCategory.IEEE_WIFI,
        ),
        StandardsReference(
            id = "IEEE 802.11n",
            name = "WiFi 4 (High Throughput)",
            description = "Introduced MIMO, 40 MHz channels, up to 600 Mbps. Operates on 2.4 GHz and 5 GHz",
            category = StandardCategory.IEEE_WIFI,
        ),
        StandardsReference(
            id = "IEEE 802.11ac",
            name = "WiFi 5 (Very High Throughput)",
            description = "5 GHz only, 80/160 MHz channels, MU-MIMO, up to 6.9 Gbps. Beamforming improvements",
            category = StandardCategory.IEEE_WIFI,
        ),
        StandardsReference(
            id = "IEEE 802.11ax",
            name = "WiFi 6 / WiFi 6E (High Efficiency)",
            description = "OFDMA, MU-MIMO improvements, Target Wake Time, up to 9.6 Gbps. WiFi 6E adds 6 GHz band",
            category = StandardCategory.IEEE_WIFI,
        ),
        StandardsReference(
            id = "IEEE 802.11be",
            name = "WiFi 7 (Extremely High Throughput)",
            description = "320 MHz channels, 4K QAM, Multi-Link Operation, up to 46 Gbps. Expected 2024",
            category = StandardCategory.IEEE_WIFI,
        ),
        // Roaming and Management Standards
        StandardsReference(
            id = "IEEE 802.11k",
            name = "Radio Resource Measurement",
            description = "Enables stations to understand radio environment. Provides neighbor reports for assisted roaming",
            category = StandardCategory.IEEE_WIFI,
        ),
        StandardsReference(
            id = "IEEE 802.11r",
            name = "Fast BSS Transition (Fast Roaming)",
            description = "Reduces roaming time from 1000ms to <50ms. Critical for VoIP and video applications",
            category = StandardCategory.IEEE_WIFI,
        ),
        StandardsReference(
            id = "IEEE 802.11v",
            name = "Wireless Network Management",
            description = "BSS Transition Management for network-assisted roaming. Load balancing and power management",
            category = StandardCategory.IEEE_WIFI,
        ),
        StandardsReference(
            id = "IEEE 802.11w",
            name = "Protected Management Frames (PMF)",
            description = "Protects management frames from spoofing and forgery. Required for WPA3",
            category = StandardCategory.SECURITY,
        ),
        // Security Standards
        StandardsReference(
            id = "WPA",
            name = "WiFi Protected Access",
            description = "Original WPA using TKIP encryption. Deprecated due to security vulnerabilities",
            category = StandardCategory.SECURITY,
        ),
        StandardsReference(
            id = "WPA2",
            name = "WiFi Protected Access 2",
            description = "Uses AES-CCMP encryption. Personal (PSK) and Enterprise (802.1X) modes. Industry standard since 2004",
            url = "https://www.wi-fi.org/discover-wi-fi/security",
            category = StandardCategory.SECURITY,
        ),
        StandardsReference(
            id = "WPA3",
            name = "WiFi Protected Access 3",
            description = "SAE (Simultaneous Authentication of Equals) replaces PSK. Forward secrecy, 192-bit mode for Enterprise",
            url = "https://www.wi-fi.org/discover-wi-fi/security",
            category = StandardCategory.SECURITY,
        ),
        StandardsReference(
            id = "IEEE 802.1X",
            name = "Port-Based Network Access Control",
            description = "Authentication framework for enterprise networks. Uses EAP methods with RADIUS server",
            category = StandardCategory.SECURITY,
        ),
        StandardsReference(
            id = "RFC 5216",
            name = "EAP-TLS",
            description = "EAP method using TLS. Most secure EAP method, requires client certificates",
            url = "https://tools.ietf.org/html/rfc5216",
            category = StandardCategory.SECURITY,
        ),
        StandardsReference(
            id = "RFC 8110",
            name = "Opportunistic Wireless Encryption (OWE)",
            description = "Encryption for open networks (Enhanced Open). Provides encryption without authentication",
            url = "https://tools.ietf.org/html/rfc8110",
            category = StandardCategory.SECURITY,
        ),
        // WiFi Alliance Certifications
        StandardsReference(
            id = "WiFi 6 Certification",
            name = "WiFi CERTIFIED 6",
            description = "WiFi Alliance certification for 802.11ax devices. Ensures interoperability and features",
            url = "https://www.wi-fi.org/discover-wi-fi/wi-fi-certified-6",
            category = StandardCategory.WIFI_ALLIANCE,
        ),
        StandardsReference(
            id = "WiFi 6E Certification",
            name = "WiFi CERTIFIED 6E",
            description = "WiFi Alliance certification for 6 GHz capable devices. Includes 6 GHz band support",
            url = "https://www.wi-fi.org/discover-wi-fi/wi-fi-certified-6e",
            category = StandardCategory.WIFI_ALLIANCE,
        ),
        StandardsReference(
            id = "WMM",
            name = "WiFi Multimedia (WMM)",
            description = "QoS for WiFi. Prioritizes voice, video, best effort, and background traffic",
            category = StandardCategory.QOS,
        ),
        StandardsReference(
            id = "WMM-Admission Control",
            name = "WMM Admission Control",
            description = "Extends WMM with bandwidth reservation for voice/video applications",
            category = StandardCategory.QOS,
        ),
        // Compliance Standards
        StandardsReference(
            id = "PCI-DSS v4.0",
            name = "Payment Card Industry Data Security Standard",
            description = "Security standard for organizations handling credit cards. Requires strong WiFi encryption",
            url = "https://www.pcisecuritystandards.org/",
            category = StandardCategory.COMPLIANCE,
        ),
        StandardsReference(
            id = "HIPAA Security Rule",
            name = "Health Insurance Portability and Accountability Act",
            description = "Requires encryption of Protected Health Information (PHI) in transit over wireless networks",
            url = "https://www.hhs.gov/hipaa/for-professionals/security/index.html",
            category = StandardCategory.COMPLIANCE,
        ),
        StandardsReference(
            id = "GDPR Article 32",
            name = "Security of Processing",
            description = "Requires appropriate technical measures including encryption for personal data protection",
            url = "https://gdpr-info.eu/art-32-gdpr/",
            category = StandardCategory.COMPLIANCE,
        ),
        StandardsReference(
            id = "NIST SP 800-97",
            name = "Establishing Wireless Robust Security Networks",
            description = "NIST guidance for secure wireless network deployment",
            url = "https://csrc.nist.gov/publications/detail/sp/800-97/final",
            category = StandardCategory.COMPLIANCE,
        ),
        // Regulatory
        StandardsReference(
            id = "FCC Part 15",
            name = "FCC Rules for Unlicensed Devices",
            description = "US regulations for WiFi in 2.4 GHz, 5 GHz, and 6 GHz bands. Power limits and DFS requirements",
            url = "https://www.fcc.gov/general/radio-frequency-safety-0",
            category = StandardCategory.REGULATORY,
        ),
        StandardsReference(
            id = "ETSI EN 300 328",
            name = "European Harmonized Standard for 2.4 GHz",
            description = "European regulations for 2.4 GHz WiFi equipment",
            category = StandardCategory.REGULATORY,
        ),
        StandardsReference(
            id = "ETSI EN 301 893",
            name = "European Harmonized Standard for 5 GHz",
            description = "European regulations for 5 GHz WiFi equipment including DFS requirements",
            category = StandardCategory.REGULATORY,
        ),
        // Additional IEEE Standards
        StandardsReference(
            id = "IEEE 802.11s",
            name = "Mesh Networking",
            description = "Defines mesh networking for WiFi. Self-configuring, multi-hop wireless backhaul",
            category = StandardCategory.IEEE_WIFI,
        ),
        StandardsReference(
            id = "IEEE 802.11ai",
            name = "Fast Initial Link Setup (FILS)",
            description = "Reduces association time for faster connections. Important for high-density scenarios",
            category = StandardCategory.IEEE_WIFI,
        ),
        StandardsReference(
            id = "IEEE 802.11mc",
            name = "Fine Timing Measurement (FTM)",
            description = "Enables WiFi-based indoor positioning with meter-level accuracy",
            category = StandardCategory.IEEE_WIFI,
        ),
    )
