package io.lamco.netkit.ieparser.model

/**
 * HE (High Efficiency) Capabilities - IEEE 802.11ax / WiFi 6
 *
 * WiFi 6 focused on efficiency in dense environments.
 *
 * Key features:
 * - OFDMA (Orthogonal Frequency Division Multiple Access)
 * - MU-MIMO uplink and downlink
 * - 1024-QAM modulation
 * - TWT (Target Wake Time) for power saving
 * - BSS Coloring to reduce interference
 * - Improved performance in 2.4 GHz band
 * - Max theoretical speed: 9.6 Gbps (8 streams, 160 MHz)
 *
 * Information Element: Extension ID 35 (within IE 255)
 * Length: Variable (minimum 21 bytes)
 *
 * Structure:
 * - Extension ID (1 byte)
 * - HE MAC Capabilities (6 bytes)
 * - HE PHY Capabilities (11 bytes)
 * - Supported HE-MCS and NSS Set (variable)
 * - PPE Thresholds (optional, variable)
 *
 * @property ofdmaSupport OFDMA support (mandatory in HE)
 * @property twtRequesterSupport Target Wake Time requester (client-side)
 * @property twtResponderSupport Target Wake Time responder (AP-side)
 * @property muMimoDownlink MU-MIMO downlink support (AP → clients)
 * @property muMimoUplink MU-MIMO uplink support (clients → AP)
 * @property supports40MHzIn24GHz 40 MHz in 2.4 GHz band
 * @property supports80MHzIn5GHz 80 MHz in 5 GHz band
 * @property supports160MHz 160 MHz channels
 * @property supports80Plus80MHz Non-contiguous 80+80 MHz
 * @property beamformeeCapable Receive beamformed transmissions
 * @property beamformerCapable Transmit beamformed signals
 * @property maxSpatialStreams Maximum MIMO spatial streams (1-8)
 * @property dualBandSupport Supports both 2.4 GHz and 5/6 GHz
 */
data class HECapabilities(
    val ofdmaSupport: Boolean = true, // Mandatory in HE
    val twtRequesterSupport: Boolean = false,
    val twtResponderSupport: Boolean = false,
    val muMimoDownlink: Boolean = false,
    val muMimoUplink: Boolean = false,
    val supports40MHzIn24GHz: Boolean = false,
    val supports80MHzIn5GHz: Boolean = false,
    val supports160MHz: Boolean = false,
    val supports80Plus80MHz: Boolean = false,
    val beamformeeCapable: Boolean = false,
    val beamformerCapable: Boolean = false,
    val maxSpatialStreams: Int = 1,
    val dualBandSupport: Boolean = false,
) {
    /**
     * Maximum channel width supported
     */
    val maxChannelWidthMHz: Int
        get() =
            when {
                supports160MHz || supports80Plus80MHz -> 160
                supports80MHzIn5GHz -> 80
                supports40MHzIn24GHz -> 40
                else -> 20
            }

    /**
     * Maximum theoretical PHY rate in Mbps
     * Based on: streams, channel width, 1024-QAM
     * HE adds ~20% over VHT due to improved efficiency
     */
    val maxPhyRateMbps: Int
        get() =
            when {
                // 8 streams, 160 MHz, 1024-QAM
                maxSpatialStreams == 8 && supports160MHz -> 9608
                maxSpatialStreams == 8 && supports80MHzIn5GHz -> 4804

                // 4 streams, 160 MHz
                maxSpatialStreams == 4 && supports160MHz -> 4804
                maxSpatialStreams == 4 && supports80MHzIn5GHz -> 2402

                // 2 streams, 160 MHz
                maxSpatialStreams == 2 && supports160MHz -> 2402
                maxSpatialStreams == 2 && supports80MHzIn5GHz -> 1201

                // 2 streams, 40 MHz (2.4 GHz)
                maxSpatialStreams == 2 && supports40MHzIn24GHz -> 600

                // 1 stream, 80 MHz
                maxSpatialStreams == 1 && supports80MHzIn5GHz -> 600

                // 1 stream, 40 MHz
                maxSpatialStreams == 1 && supports40MHzIn24GHz -> 287

                else -> 143 // 1 stream, 20 MHz
            }

    /**
     * Whether this is a high-performance HE configuration
     * (80+ MHz, 2+ streams, OFDMA, TWT)
     */
    val isHighPerformance: Boolean
        get() =
            supports80MHzIn5GHz &&
                maxSpatialStreams >= 2 &&
                ofdmaSupport &&
                (twtRequesterSupport || twtResponderSupport)

    /**
     * Whether this supports WiFi 6 efficiency features
     */
    val hasEfficiencyFeatures: Boolean
        get() = ofdmaSupport && (twtRequesterSupport || twtResponderSupport)

    /**
     * Whether this is optimized for dense environments
     * (OFDMA + MU-MIMO + beamforming)
     */
    val isDenseEnvironmentOptimized: Boolean
        get() =
            ofdmaSupport &&
                (muMimoDownlink || muMimoUplink) &&
                (beamformeeCapable || beamformerCapable)
}

/**
 * HE Operation Information - WiFi 6 operational parameters
 *
 * Information Element: Extension ID 36 (within IE 255)
 *
 * Contains operational parameters for a WiFi 6 BSS.
 *
 * @property bssColor BSS Color value (0-63, 0 means disabled)
 * @property dualBandMode Whether AP operates on both 2.4 and 5/6 GHz
 * @property twtActive Whether TWT is currently active in BSS
 */
data class HEOperation(
    val bssColor: Int = 0,
    val dualBandMode: Boolean = false,
    val twtActive: Boolean = false,
) {
    /**
     * Whether BSS Coloring is enabled
     * BSS Color helps reduce interference in dense deployments
     */
    val bssColorEnabled: Boolean
        get() = bssColor in 1..63

    /**
     * BSS Color provides spatial reuse benefits
     * Devices can ignore transmissions from other BSSs with different colors
     */
    val supportsSpatialReuse: Boolean
        get() = bssColorEnabled
}
