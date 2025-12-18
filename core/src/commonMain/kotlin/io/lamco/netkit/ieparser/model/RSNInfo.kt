package io.lamco.netkit.ieparser.model

/**
 * RSN (Robust Security Network) Information Element
 *
 * Contains comprehensive security parameters for WPA2/WPA3 networks.
 *
 * Structure (IEEE 802.11-2020 Section 9.4.2.25):
 * - Version (2 bytes)
 * - Group Cipher Suite (4 bytes)
 * - Pairwise Cipher Suite Count + List
 * - AKM Suite Count + List
 * - RSN Capabilities (2 bytes)
 * - PMKID Count + List (optional)
 * - Group Management Cipher Suite (optional)
 *
 * @property version RSN version (should be 1)
 * @property groupCipher Cipher for broadcast/multicast traffic
 * @property pairwiseCiphers List of supported pairwise (unicast) ciphers
 * @property akmSuites List of supported authentication methods
 * @property pmfCapable Device supports Protected Management Frames (optional)
 * @property pmfRequired PMF is mandatory (WPA3 requirement)
 * @property beaconProtectionCapable Supports encrypted beacon frames (WPA3 2024)
 * @property beaconProtectionRequired Beacon encryption is mandatory
 */
data class RSNInfo(
    val version: Int = 1,
    val groupCipher: CipherSuite? = null,
    val pairwiseCiphers: List<CipherSuite> = emptyList(),
    val akmSuites: List<AKMSuite> = emptyList(),
    val pmfCapable: Boolean = false,
    val pmfRequired: Boolean = false,
    val beaconProtectionCapable: Boolean = false,
    val beaconProtectionRequired: Boolean = false,
) {
    /**
     * Whether this network supports WPA3
     */
    val isWPA3: Boolean
        get() = akmSuites.any { it.isWPA3 }

    /**
     * Whether this network supports WPA3-SAE-PK (industry-first detection)
     */
    val hasSaePk: Boolean
        get() = AKMSuite.SAE_PK in akmSuites

    /**
     * Whether this network uses deprecated ciphers (TKIP, WEP)
     */
    val hasDeprecatedCiphers: Boolean
        get() =
            pairwiseCiphers.any { it.deprecated } ||
                groupCipher?.deprecated == true

    /**
     * List of deprecated ciphers in use
     */
    val deprecatedCiphers: List<CipherSuite>
        get() =
            buildList {
                groupCipher?.let { if (it.deprecated) add(it) }
                addAll(pairwiseCiphers.filter { it.deprecated })
            }

    /**
     * Highest security level cipher (0-100)
     */
    val bestCipherSecurityLevel: Int
        get() = pairwiseCiphers.maxOfOrNull { it.securityLevel } ?: 0

    /**
     * Highest security level AKM (0-100)
     */
    val bestAKMSecurityLevel: Int
        get() = akmSuites.maxOfOrNull { it.securityLevel } ?: 0

    /**
     * Overall RSN security score (0-100)
     * Considers: ciphers, AKM, PMF, beacon protection
     */
    val securityScore: Int
        get() {
            var score = 0

            // AKM contributes 50 points
            score += (bestAKMSecurityLevel * 0.5).toInt()

            // Cipher contributes 30 points
            score += (bestCipherSecurityLevel * 0.3).toInt()

            // PMF contributes 10 points
            when {
                pmfRequired -> score += 10
                pmfCapable -> score += 5
            }

            // Beacon Protection contributes 10 points
            when {
                beaconProtectionRequired -> score += 10
                beaconProtectionCapable -> score += 5
            }

            return score.coerceIn(0, 100)
        }
}

/**
 * RSN Extension Information Element (ID 244)
 *
 * Contains enhanced SAE parameters:
 * - H2E (Hash-to-Element) support
 * - SAE-PK password identifier
 *
 * @property h2eSupport Whether network supports Hash-to-Element (enhanced SAE)
 * @property saePkIdentifier SAE-PK password identifier (if present)
 */
data class RSNExtensionInfo(
    val h2eSupport: Boolean = false,
    val saePkIdentifier: String? = null, // Not parsed - see IEParser parseRSNExtension()
)
