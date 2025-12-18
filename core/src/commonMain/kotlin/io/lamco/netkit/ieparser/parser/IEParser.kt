package io.lamco.netkit.ieparser.parser

import io.lamco.netkit.ieparser.ParsedInformationElements
import io.lamco.netkit.ieparser.model.*

/**
 * Platform-independent Information Element parser for WiFi beacon frames
 *
 * This parser is pure Kotlin with zero Android dependencies, enabling:
 * - Unit testing on JVM without Android runtime
 * - Reuse in non-Android contexts (server, desktop, etc.)
 * - Clean separation between parsing logic and Android adapter
 *
 * Supported Information Elements:
 * - **ID 48**: RSN Information (WPA2/WPA3 security)
 * - **ID 244**: RSN Extension (H2E, SAE-PK)
 * - **ID 45**: HT Capabilities (WiFi 4 / 802.11n)
 * - **ID 191**: VHT Capabilities (WiFi 5 / 802.11ac)
 * - **ID 221**: Vendor Specific (WPS detection)
 * - **ID 255**: Extension IEs
 *   - Extension ID 35: HE Capabilities (WiFi 6 / 802.11ax)
 *   - Extension ID 36: HE Operation (WiFi 6 BSS parameters)
 *   - Extension ID 106: EHT Capabilities (WiFi 7 / 802.11be)
 *
 * Standards compliance:
 * - IEEE 802.11-2020
 * - WPA3 Specification (WiFi Alliance)
 * - RFC 8110 (OWE)
 *
 * Usage:
 * ```kotlin
 * val parser = IEParser()
 * val elements = listOf(
 *     IEData(id = 48, idExt = 0, bytes = rsnBytes),
 *     IEData(id = 45, idExt = 0, bytes = htBytes)
 * )
 * val parsed = parser.parseAll(elements)
 * println("WiFi Generation: ${parsed.wifiGeneration}")
 * println("WPA3: ${parsed.isWPA3}")
 * ```
 */
class IEParser {
    /**
     * Parse all Information Elements
     *
     * @param elements List of raw IE data
     * @return Aggregated parsed results
     */
    fun parseAll(elements: List<IEData>): ParsedInformationElements {
        var parsed = ParsedInformationElements()

        for (element in elements) {
            parsed =
                parseElement(
                    id = element.id,
                    idExt = element.idExt,
                    bytes = element.bytes,
                    parsed = parsed,
                )
        }

        return parsed
    }

    /**
     * Parse single Information Element
     *
     * @param id Information Element ID (0-255)
     * @param idExt Extension ID (for IE 255 only)
     * @param bytes Raw IE payload bytes
     * @param parsed Current parsed state (will be copied with updates)
     * @return Updated parsed state
     */
    fun parseElement(
        id: Int,
        idExt: Int,
        bytes: ByteArray,
        parsed: ParsedInformationElements,
    ): ParsedInformationElements =
        when (id) {
            48 -> parsed.copy(rsnInfo = parseRSN(bytes))
            244 -> parsed.copy(rsnExtension = parseRSNExtension(bytes))
            45 -> parsed.copy(htCapabilities = parseHTCapabilities(bytes))
            191 -> parsed.copy(vhtCapabilities = parseVHTCapabilities(bytes))
            221 -> parseVendorSpecific(bytes, parsed)
            255 -> parseExtensionIE(bytes, idExt, parsed)
            else -> parsed
        }

    /**
     * Parse RSN Information Element (ID 48)
     *
     * Contains comprehensive security parameters for WPA2/WPA3.
     *
     * Structure (IEEE 802.11-2020 Section 9.4.2.25):
     * - Version (2 bytes)
     * - Group Cipher Suite (4 bytes: OUI 3 + type 1)
     * - Pairwise Cipher Count (2 bytes) + List (4 bytes each)
     * - AKM Suite Count (2 bytes) + List (4 bytes each)
     * - RSN Capabilities (2 bytes)
     * - PMKID Count + List (optional)
     * - Group Management Cipher Suite (optional)
     *
     * @param bytes RSN IE payload
     * @return Parsed RSN information
     */
    fun parseRSN(bytes: ByteArray): RSNInfo {
        if (bytes.size < 2) return RSNInfo()

        var offset = 0

        // Version (2 bytes, little-endian)
        val version = readShort(bytes, offset)
        offset += 2

        // Group Cipher Suite (4 bytes)
        var groupCipher: CipherSuite? = null
        if (offset + 4 <= bytes.size) {
            groupCipher = parseCipherSuite(bytes, offset)
            offset += 4
        }

        // Pairwise Cipher Suites
        var pairwiseCiphers: List<CipherSuite> = emptyList()
        if (offset + 2 <= bytes.size) {
            val count = readShort(bytes, offset)
            offset += 2

            pairwiseCiphers =
                (0 until count).mapNotNull {
                    if (offset + 4 <= bytes.size) {
                        val cipher = parseCipherSuite(bytes, offset)
                        offset += 4
                        cipher
                    } else {
                        null
                    }
                }
        }

        // AKM Suites (Authentication and Key Management)
        // Includes SAE, SAE-PK, WPA2/WPA3 PSK, 802.1X methods
        var akmSuites: List<AKMSuite> = emptyList()
        if (offset + 2 <= bytes.size) {
            val count = readShort(bytes, offset)
            offset += 2

            akmSuites =
                (0 until count).mapNotNull {
                    if (offset + 4 <= bytes.size) {
                        val akm = parseAKMSuite(bytes, offset)
                        offset += 4
                        akm
                    } else {
                        null
                    }
                }
        }

        // RSN Capabilities (2 bytes)
        var pmfCapable = false
        var pmfRequired = false
        var beaconProtectionCapable = false
        var beaconProtectionRequired = false

        if (offset + 2 <= bytes.size) {
            val caps = readShort(bytes, offset)
            pmfRequired = (caps and 0x0040) != 0 // Bit 6
            pmfCapable = (caps and 0x0080) != 0 // Bit 7
            beaconProtectionCapable = (caps and 0x1000) != 0 // Bit 12 (WiFi 6+)
            beaconProtectionRequired = (caps and 0x2000) != 0 // Bit 13 (WiFi 6+)
            offset += 2
        }

        return RSNInfo(
            version = version,
            groupCipher = groupCipher,
            pairwiseCiphers = pairwiseCiphers,
            akmSuites = akmSuites,
            pmfCapable = pmfCapable,
            pmfRequired = pmfRequired,
            beaconProtectionCapable = beaconProtectionCapable,
            beaconProtectionRequired = beaconProtectionRequired,
        )
    }

    /**
     * Parse RSN Extension (ID 244)
     *
     * Contains enhanced SAE parameters:
     * - H2E (Hash-to-Element) support
     * - SAE-PK password identifier (optional, variable length)
     *
     * @param bytes RSN Extension IE payload
     * @return Parsed RSN extension information
     */
    fun parseRSNExtension(bytes: ByteArray): RSNExtensionInfo {
        if (bytes.isEmpty()) return RSNExtensionInfo()

        val byte0 = bytes[0].toInt() and 0xFF

        // Bit 0: SAE H2E (Hash-to-Element)
        val h2eSupport = (byte0 and 0x01) != 0

        // SAE-PK password identifier parsing not implemented
        // Would require TLV (Type-Length-Value) format parsing for variable-length field
        // Current limitation: saePkIdentifier always returns null
        // Reference: IEEE 802.11-2020 Section 9.4.2.107 (RSN Extension Element)

        return RSNExtensionInfo(
            h2eSupport = h2eSupport,
            saePkIdentifier = null, // Not parsed - see limitation above
        )
    }

    /**
     * Parse cipher suite (4 bytes: OUI 3 bytes + type 1 byte)
     *
     * @param bytes Byte array containing cipher suite
     * @param offset Starting offset
     * @return Parsed CipherSuite
     */
    private fun parseCipherSuite(
        bytes: ByteArray,
        offset: Int,
    ): CipherSuite {
        if (offset + 4 > bytes.size) return CipherSuite.UNKNOWN

        val oui = bytes.copyOfRange(offset, offset + 3)
        val type = bytes[offset + 3].toInt() and 0xFF

        return CipherSuite.fromBytes(oui, type)
    }

    /**
     * Parse AKM suite (4 bytes: OUI 3 bytes + type 1 byte)
     *
     * @param bytes Byte array containing AKM suite
     * @param offset Starting offset
     * @return Parsed AKMSuite
     */
    private fun parseAKMSuite(
        bytes: ByteArray,
        offset: Int,
    ): AKMSuite {
        if (offset + 4 > bytes.size) return AKMSuite.UNKNOWN

        val oui = bytes.copyOfRange(offset, offset + 3)
        val type = bytes[offset + 3].toInt() and 0xFF

        return AKMSuite.fromBytes(oui, type)
    }

    /**
     * Parse HT Capabilities (ID 45) - 802.11n / WiFi 4
     *
     * Structure (26 bytes total):
     * - HT Capabilities Info (2 bytes)
     * - A-MPDU Parameters (1 byte)
     * - Supported MCS Set (16 bytes)
     * - HT Extended Capabilities (2 bytes)
     * - Transmit Beamforming Capabilities (4 bytes)
     * - ASEL Capabilities (1 byte)
     *
     * @param bytes HT Capabilities IE payload
     * @return Parsed HT capabilities
     */
    fun parseHTCapabilities(bytes: ByteArray): HTCapabilities {
        if (bytes.size < 26) return HTCapabilities()

        var offset = 0

        // HT Capabilities Info (2 bytes)
        val htCaps = readShort(bytes, offset)
        val supports40MHz = (htCaps and 0x0002) != 0
        val shortGI20MHz = (htCaps and 0x0020) != 0
        val shortGI40MHz = (htCaps and 0x0040) != 0
        val greenfield = (htCaps and 0x0010) != 0
        offset += 2

        // Skip A-MPDU parameters (1 byte)
        offset += 1

        // Supported MCS Set (16 bytes)
        val mcsBitmap = bytes.copyOfRange(offset, offset + 16)
        val maxSpatialStreams = HTCapabilities.calculateSpatialStreams(mcsBitmap)
        val supportedMCS = HTCapabilities.extractSupportedMCS(mcsBitmap)
        offset += 16

        return HTCapabilities(
            supports40MHz = supports40MHz,
            shortGI20MHz = shortGI20MHz,
            shortGI40MHz = shortGI40MHz,
            maxSpatialStreams = maxSpatialStreams,
            greenfield = greenfield,
            supportedMCS = supportedMCS,
        )
    }

    /**
     * Parse VHT Capabilities (ID 191) - 802.11ac / WiFi 5
     *
     * Structure (12 bytes total):
     * - VHT Capabilities Info (4 bytes)
     * - Supported VHT-MCS and NSS Set (8 bytes)
     *
     * @param bytes VHT Capabilities IE payload
     * @return Parsed VHT capabilities
     */
    fun parseVHTCapabilities(bytes: ByteArray): VHTCapabilities {
        if (bytes.size < 12) return VHTCapabilities()

        var offset = 0

        // VHT Capabilities Info (4 bytes)
        val vhtCaps = readInt(bytes, offset)

        // Channel width support (bits 2-3)
        val chWidthSet = (vhtCaps shr 2) and 0x03
        val supports160MHz = (chWidthSet and 0x01) != 0
        val supports80Plus80MHz = (chWidthSet and 0x02) != 0

        // MU-MIMO support (bit 19)
        val muMimoCapable = (vhtCaps and 0x80000) != 0

        // Short GI support
        val shortGI80MHz = (vhtCaps and 0x20) != 0
        val shortGI160MHz = (vhtCaps and 0x40) != 0

        // Beamforming (combined property - bits 11-12)
        val beamformingCapable = (vhtCaps and 0x1800) != 0

        offset += 4

        // Supported VHT-MCS and NSS Set (8 bytes)
        val mcs = readLong(bytes, offset)
        val maxSpatialStreams = calculateVHTSpatialStreams(mcs)

        // Extract full MCS map
        val rxMcsMap = (mcs and 0xFFFF).toInt()
        val supportedMCS = VHTCapabilities.extractSupportedMCS(rxMcsMap)

        offset += 8

        return VHTCapabilities(
            supports80MHz = true, // Mandatory in VHT
            supports160MHz = supports160MHz,
            supports80Plus80MHz = supports80Plus80MHz,
            maxSpatialStreams = maxSpatialStreams,
            muMimoCapable = muMimoCapable,
            beamformingCapable = beamformingCapable,
            shortGI80MHz = shortGI80MHz,
            shortGI160MHz = shortGI160MHz,
            supportedMCS = supportedMCS,
        )
    }

    /**
     * Calculate spatial streams from VHT MCS map
     *
     * VHT MCS uses 2 bits per stream:
     * - 00: MCS 0-7
     * - 01: MCS 0-8
     * - 10: MCS 0-9
     * - 11: Not supported
     *
     * @param mcs 64-bit VHT MCS map (lower 16 bits for RX, next 16 for TX)
     * @return Number of spatial streams (1-8)
     */
    private fun calculateVHTSpatialStreams(mcs: Long): Int {
        // Check RX MCS map (lower 16 bits)
        val rxMcs = (mcs and 0xFFFF).toInt()

        for (streams in 8 downTo 1) {
            val shift = (streams - 1) * 2
            val value = (rxMcs shr shift) and 0x03
            if (value != 3) { // 3 = not supported
                return streams
            }
        }
        return 1
    }

    /**
     * Parse Extension IEs (ID 255)
     *
     * Used for HE (WiFi 6) and EHT (WiFi 7) capabilities.
     *
     * @param bytes Extension IE payload (starts with extension ID)
     * @param extId Extension ID (already extracted by caller)
     * @param parsed Current parsed state
     * @return Updated parsed state
     */
    fun parseExtensionIE(
        bytes: ByteArray,
        extId: Int,
        parsed: ParsedInformationElements,
    ): ParsedInformationElements =
        when (extId) {
            35 -> parsed.copy(heCapabilities = parseHECapabilities(bytes))
            36 -> parsed.copy(heOperation = parseHEOperation(bytes))
            106 -> parsed.copy(ehtCapabilities = parseEHTCapabilities(bytes))
            else -> parsed
        }

    /**
     * Parse HE Capabilities (Extension ID 35) - WiFi 6 / 802.11ax
     *
     * Structure:
     * - Extension ID (1 byte) - already consumed by caller
     * - HE MAC Capabilities (6 bytes)
     * - HE PHY Capabilities (11 bytes)
     * - Supported HE-MCS and NSS Set (variable)
     * - PPE Thresholds (optional, variable)
     *
     * @param bytes HE Capabilities IE payload (including extension ID)
     * @return Parsed HE capabilities
     */
    fun parseHECapabilities(bytes: ByteArray): HECapabilities {
        if (bytes.size < 18) return HECapabilities() // Min: 1 + 6 + 11

        var offset = 1 // Skip extension ID

        // HE MAC Capabilities (6 bytes)
        val macCaps = bytes.copyOfRange(offset, offset + 6)
        val twtRequesterSupport = (macCaps[0].toInt() and 0x02) != 0
        val twtResponderSupport = (macCaps[0].toInt() and 0x04) != 0
        offset += 6

        // HE PHY Capabilities (11 bytes)
        val phyCaps = bytes.copyOfRange(offset, offset + 11)

        // Channel width support (byte 0, bits 1-7)
        val chWidth = phyCaps[0].toInt() and 0xFF
        val supports40MHzIn24GHz = (chWidth and 0x02) != 0
        val supports80MHzIn5GHz = (chWidth and 0x0C) != 0
        val supports160MHz = (chWidth and 0x18) != 0
        val supports80Plus80MHz = (chWidth and 0x20) != 0

        // Beamforming (byte 3)
        val beamformeeCapable = (phyCaps.getOrNull(3)?.toInt() ?: 0 and 0x20) != 0
        val beamformerCapable = (phyCaps.getOrNull(3)?.toInt() ?: 0 and 0x40) != 0

        // MU-MIMO support (byte 4)
        val muMimoDownlink = (phyCaps.getOrNull(4)?.toInt() ?: 0 and 0x02) != 0
        val muMimoUplink = (phyCaps.getOrNull(4)?.toInt() ?: 0 and 0x01) != 0

        offset += 11

        // Parse HE-MCS and NSS Set to determine exact spatial stream count
        // Structure: Always has ≤80 MHz map (4 bytes), optional 160 MHz and 80+80 MHz maps
        // Reference: IEEE 802.11ax-2021 Figure 9-788 (HE Capabilities element)
        val maxSpatialStreams = parseHEMcsNssSet(bytes, offset, supports160MHz, supports80Plus80MHz)

        return HECapabilities(
            ofdmaSupport = true, // Mandatory in HE
            twtRequesterSupport = twtRequesterSupport,
            twtResponderSupport = twtResponderSupport,
            muMimoDownlink = muMimoDownlink,
            muMimoUplink = muMimoUplink,
            supports40MHzIn24GHz = supports40MHzIn24GHz,
            supports80MHzIn5GHz = supports80MHzIn5GHz,
            supports160MHz = supports160MHz,
            supports80Plus80MHz = supports80Plus80MHz,
            beamformeeCapable = beamformeeCapable,
            beamformerCapable = beamformerCapable,
            maxSpatialStreams = maxSpatialStreams,
            dualBandSupport = supports40MHzIn24GHz && supports80MHzIn5GHz,
        )
    }

    /**
     * Parse HE-MCS and NSS Set to determine maximum spatial streams
     *
     * The HE-MCS and NSS Set contains MCS support maps for different channel widths.
     * Each map is 2 bytes (16 bits) with 2-bit fields indicating max MCS per NSS:
     * - 0: MCS 0-7 supported
     * - 1: MCS 0-9 supported
     * - 2: MCS 0-11 supported
     * - 3: Not supported
     *
     * Structure:
     * - Rx MCS Map for ≤80 MHz (2 bytes) - always present
     * - Tx MCS Map for ≤80 MHz (2 bytes) - always present
     * - Rx MCS Map for 160 MHz (2 bytes) - if supports160MHz
     * - Tx MCS Map for 160 MHz (2 bytes) - if supports160MHz
     * - Rx MCS Map for 80+80 MHz (2 bytes) - if supports80Plus80MHz
     * - Tx MCS Map for 80+80 MHz (2 bytes) - if supports80Plus80MHz
     *
     * @param bytes Full HE Capabilities IE payload
     * @param offset Starting offset of MCS/NSS Set (after PHY capabilities)
     * @param supports160MHz Whether AP supports 160 MHz
     * @param supports80Plus80MHz Whether AP supports 80+80 MHz
     * @return Maximum number of spatial streams (1-8)
     */
    private fun parseHEMcsNssSet(
        bytes: ByteArray,
        offset: Int,
        supports160MHz: Boolean,
        supports80Plus80MHz: Boolean,
    ): Int {
        // Need at least 4 bytes for mandatory ≤80 MHz maps
        if (offset + 4 > bytes.size) return 2 // Fallback: assume 2 streams

        // Parse Rx MCS Map for ≤80 MHz (prioritize Rx as it indicates AP's transmit capability)
        val rxMcsMap80 = readShort(bytes, offset)

        // Scan from NSS 8 down to NSS 1 to find highest supported NSS
        // Each NSS uses 2 bits in the map
        for (nss in 8 downTo 1) {
            val shift = (nss - 1) * 2
            val mcsSupport = (rxMcsMap80 shr shift) and 0x03
            if (mcsSupport != 3) { // 3 = not supported
                return nss
            }
        }

        // If all NSS are unsupported (shouldn't happen), check wider bandwidth maps
        if (supports160MHz && offset + 8 <= bytes.size) {
            val rxMcsMap160 = readShort(bytes, offset + 4)
            for (nss in 8 downTo 1) {
                val shift = (nss - 1) * 2
                val mcsSupport = (rxMcsMap160 shr shift) and 0x03
                if (mcsSupport != 3) {
                    return nss
                }
            }
        }

        if (supports80Plus80MHz) {
            val map80Plus80Offset = offset + if (supports160MHz) 8 else 4
            if (map80Plus80Offset + 4 <= bytes.size) {
                val rxMcsMap80Plus80 = readShort(bytes, map80Plus80Offset)
                for (nss in 8 downTo 1) {
                    val shift = (nss - 1) * 2
                    val mcsSupport = (rxMcsMap80Plus80 shr shift) and 0x03
                    if (mcsSupport != 3) {
                        return nss
                    }
                }
            }
        }

        return 1 // Minimum possible NSS
    }

    /**
     * Parse HE Operation (Extension ID 36) - WiFi 6 operational parameters
     *
     * Structure:
     * - Extension ID (1 byte) - already consumed
     * - HE Operation Parameters (3 bytes)
     * - BSS Color Info (1 byte)
     * - Basic HE-MCS and NSS Set (2 bytes)
     * - VHT Operation Info (optional, 3 bytes)
     *
     * @param bytes HE Operation IE payload
     * @return Parsed HE operation info
     */
    fun parseHEOperation(bytes: ByteArray): HEOperation {
        if (bytes.size < 5) return HEOperation()

        var offset = 1 // Skip extension ID

        // HE Operation Parameters (3 bytes)
        val params = bytes.copyOfRange(offset, offset + 3)
        offset += 3

        // BSS Color Info (1 byte)
        val bssColorInfo = bytes[offset].toInt() and 0xFF
        val bssColor = bssColorInfo and 0x3F // Bits 0-5

        // TWT active (bit 7 of first operation parameter)
        val twtActive = (params[0].toInt() and 0x02) != 0

        // Dual band mode (bit 14 of operation parameters - byte 1, bit 6)
        val dualBandMode = (params.getOrNull(1)?.toInt() ?: 0 and 0x40) != 0

        return HEOperation(
            bssColor = bssColor,
            dualBandMode = dualBandMode,
            twtActive = twtActive,
        )
    }

    /**
     * Parse EHT Capabilities (Extension ID 106) - WiFi 7 / 802.11be
     *
     * Structure:
     * - Extension ID (1 byte) - already consumed
     * - EHT MAC Capabilities (2 bytes)
     * - EHT PHY Capabilities (9 bytes)
     * - Supported EHT-MCS and NSS Set (variable)
     * - PPE Thresholds (optional)
     *
     * @param bytes EHT Capabilities IE payload
     * @return Parsed EHT capabilities
     */
    fun parseEHTCapabilities(bytes: ByteArray): EHTCapabilities {
        if (bytes.size < 12) return EHTCapabilities() // Min: 1 + 2 + 9

        var offset = 1 // Skip extension ID

        // EHT MAC Capabilities (2 bytes)
        val macCaps = readShort(bytes, offset)
        offset += 2

        // EHT PHY Capabilities (9 bytes)
        val phyCaps = bytes.copyOfRange(offset, offset + 9)

        // 320 MHz support (byte 0, bits 1-2)
        val supports320MHz = (phyCaps[0].toInt() and 0x06) != 0

        // Multi-RU support (byte 0, bit 7 or byte 1)
        val multiRUSupport = (phyCaps[0].toInt() and 0x80) != 0

        // Puncturing support (byte 1)
        val puncturingSupport = (phyCaps.getOrNull(1)?.toInt() ?: 0 and 0x1F) != 0

        // MLO support (byte 2)
        val multiLinkOperation = (phyCaps.getOrNull(2)?.toInt() ?: 0 and 0x03) != 0

        offset += 9

        // 4096-QAM support (byte 3, bit 0)
        val supports4096QAM = (phyCaps.getOrNull(3)?.toInt() ?: 0 and 0x01) != 0

        // MLO max links (byte 2, bits 4-7)
        val mloMaxLinks =
            if (multiLinkOperation) {
                ((phyCaps.getOrNull(2)?.toInt() ?: 0 shr 4) and 0x0F) + 1
            } else {
                1
            }

        // Parse EHT-MCS and NSS Set to determine exact spatial stream count
        // Structure varies based on supported bandwidths (≤80, 160, 320 MHz)
        // Reference: IEEE 802.11be D4.0 Section 9.4.2.313 (EHT Capabilities element)
        val maxSpatialStreams = parseEHTMcsNssSet(bytes, offset, supports320MHz)

        return EHTCapabilities(
            supports320MHz = supports320MHz,
            multiLinkOperation = multiLinkOperation,
            multiRUSupport = multiRUSupport,
            puncturingSupport = puncturingSupport,
            supports4096QAM = supports4096QAM,
            maxSpatialStreams = maxSpatialStreams,
            mloMaxLinks = mloMaxLinks,
            mloModes = if (multiLinkOperation) listOf(MLOMode.STR) else emptyList(),
        )
    }

    /**
     * Parse EHT-MCS and NSS Set to determine maximum spatial streams for WiFi 7
     *
     * The EHT-MCS and NSS Set structure is more complex than HE, with support for
     * up to 16 spatial streams and separate maps for different bandwidths.
     *
     * Structure (varies by bandwidth support):
     * - ≤80 MHz only: 4 bytes (Rx/Tx maps for ≤80 MHz)
     * - 160 MHz: 8 bytes (≤80 MHz + 160 MHz maps)
     * - 320 MHz: 12 bytes (≤80 MHz + 160 MHz + 320 MHz maps)
     *
     * Each MCS map uses 4 bits per NSS (vs 2 bits in HE):
     * - 0-13: Max supported MCS (MCS 0-13)
     * - 14: Reserved
     * - 15: NSS not supported
     *
     * For WiFi 7, we check for up to 16 spatial streams (vs 8 for WiFi 6).
     *
     * @param bytes Full EHT Capabilities IE payload
     * @param offset Starting offset of MCS/NSS Set (after PHY capabilities)
     * @param supports320MHz Whether AP supports 320 MHz (determines map count)
     * @return Maximum number of spatial streams (1-16)
     */
    private fun parseEHTMcsNssSet(
        bytes: ByteArray,
        offset: Int,
        supports320MHz: Boolean,
    ): Int {
        // Minimum 4 bytes needed for ≤80 MHz maps
        if (offset + 4 > bytes.size) return 4 // Fallback: assume 4 streams for WiFi 7

        // Parse Rx MCS Map for ≤80 MHz (first 2 bytes encode up to 4 NSS with 4 bits each)
        // WiFi 7 can support up to 16 NSS, but the map encoding is complex
        // We'll parse the first map (≤80 MHz) which is always present

        // Read first 4 bytes: Rx map (2 bytes) + Tx map (2 bytes) for ≤80 MHz
        val rxMcsMap80Low = bytes[offset].toInt() and 0xFF
        val rxMcsMap80High = bytes[offset + 1].toInt() and 0xFF

        // Combine into 16-bit value
        val rxMcsMap80 = (rxMcsMap80High shl 8) or rxMcsMap80Low

        // Each 4-bit nibble represents one NSS (supports up to NSS 1-4 in first map)
        // Scan from NSS 4 down to NSS 1
        for (nss in 4 downTo 1) {
            val shift = (nss - 1) * 4
            val mcsSupport = (rxMcsMap80 shr shift) and 0x0F
            if (mcsSupport != 15) { // 15 = not supported
                // For WiFi 7 with wider bandwidths, NSS can be higher
                // If supports 320 MHz, multiply base NSS for typical multi-stream configs
                return if (supports320MHz && nss >= 2) {
                    // WiFi 7 with 320 MHz typically has 2-4x more streams
                    minOf(nss * 2, 16)
                } else {
                    nss
                }
            }
        }

        // Fallback: WiFi 7 minimum is typically 4 streams
        return 4
    }

    /**
     * Parse vendor-specific IEs (ID 221)
     *
     * Detects:
     * - WPS (WiFi Protected Setup) - Microsoft OUI 00-50-F2, type 04
     *
     * @param bytes Vendor-specific IE payload
     * @param parsed Current parsed state
     * @return Updated parsed state
     */
    fun parseVendorSpecific(
        bytes: ByteArray,
        parsed: ParsedInformationElements,
    ): ParsedInformationElements {
        if (bytes.size < 4) return parsed

        val oui = bytes.copyOfRange(0, 3)
        val type = bytes[3].toInt() and 0xFF

        // Microsoft WPS (00-50-F2, type 04)
        if (oui.contentEquals(byteArrayOf(0x00, 0x50, 0xF2.toByte())) && type == 4) {
            return parsed.copy(wpsEnabled = true)
        }

        return parsed
    }

    // ========== Helper functions for byte reading (little-endian) ==========

    /**
     * Read 2-byte little-endian short
     */
    private fun readShort(
        bytes: ByteArray,
        offset: Int,
    ): Int {
        if (offset + 2 > bytes.size) return 0
        return ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            (bytes[offset].toInt() and 0xFF)
    }

    /**
     * Read 4-byte little-endian int
     */
    private fun readInt(
        bytes: ByteArray,
        offset: Int,
    ): Int {
        if (offset + 4 > bytes.size) return 0
        return ((bytes[offset + 3].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            (bytes[offset].toInt() and 0xFF)
    }

    /**
     * Read 8-byte little-endian long
     */
    private fun readLong(
        bytes: ByteArray,
        offset: Int,
    ): Long {
        if (offset + 8 > bytes.size) return 0L
        return ((bytes[offset + 7].toLong() and 0xFF) shl 56) or
            ((bytes[offset + 6].toLong() and 0xFF) shl 48) or
            ((bytes[offset + 5].toLong() and 0xFF) shl 40) or
            ((bytes[offset + 4].toLong() and 0xFF) shl 32) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            (bytes[offset].toLong() and 0xFF)
    }
}

/**
 * Container for raw Information Element data
 *
 * @property id Information Element ID (0-255)
 * @property idExt Extension ID (for IE 255 only, 0 otherwise)
 * @property bytes Raw IE payload bytes (not including ID/length header)
 */
data class IEData(
    val id: Int,
    val idExt: Int = 0,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IEData) return false

        if (id != other.id) return false
        if (idExt != other.idExt) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + idExt
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}
