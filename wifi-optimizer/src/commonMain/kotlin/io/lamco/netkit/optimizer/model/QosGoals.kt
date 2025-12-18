package io.lamco.netkit.optimizer.model

/**
 * Quality of Service (QoS) optimization goals
 *
 * Defines the target QoS characteristics for WMM (WiFi Multimedia) configuration
 * and airtime management. The optimizer will analyze current QoS settings and
 * recommend improvements to achieve these goals.
 *
 * QoS goals specify priorities for different traffic types:
 * - Voice (VoIP): Latency-sensitive, requires consistent low latency
 * - Video: Bandwidth and latency-sensitive, requires sustained throughput
 * - Data: Throughput-sensitive, can tolerate latency
 * - Background: Best-effort, lowest priority
 *
 * @property enableWmm Whether WMM should be enabled
 * @property voicePriority Priority level for voice traffic (0-3, higher = more priority)
 * @property videoPriority Priority level for video traffic
 * @property dataPriority Priority level for general data traffic
 * @property backgroundPriority Priority level for background traffic
 * @property airtimeFairnessThreshold Maximum acceptable unfairness (Gini coefficient 0.0-1.0)
 * @property maxVoiceLatencyMs Maximum acceptable latency for voice traffic (milliseconds)
 * @property minVideoThroughputMbps Minimum sustained throughput for video (Mbps)
 * @property allowAirtimeHogs Whether to allow clients to consume >30% of airtime
 *
 * @throws IllegalArgumentException if goals are invalid or contradictory
 */
data class QosGoals(
    val enableWmm: Boolean = true,
    val voicePriority: Int = 3,
    val videoPriority: Int = 2,
    val dataPriority: Int = 1,
    val backgroundPriority: Int = 0,
    val airtimeFairnessThreshold: Double = 0.4,
    val maxVoiceLatencyMs: Int = 20,
    val minVideoThroughputMbps: Int = 10,
    val allowAirtimeHogs: Boolean = false
) {
    init {
        require(voicePriority in 0..3) {
            "Voice priority must be 0-3, got $voicePriority"
        }
        require(videoPriority in 0..3) {
            "Video priority must be 0-3, got $videoPriority"
        }
        require(dataPriority in 0..3) {
            "Data priority must be 0-3, got $dataPriority"
        }
        require(backgroundPriority in 0..3) {
            "Background priority must be 0-3, got $backgroundPriority"
        }
        require(airtimeFairnessThreshold in 0.0..1.0) {
            "Airtime fairness threshold must be 0.0-1.0, got $airtimeFairnessThreshold"
        }
        require(maxVoiceLatencyMs > 0) {
            "Max voice latency must be positive, got $maxVoiceLatencyMs ms"
        }
        require(maxVoiceLatencyMs <= 100) {
            "Max voice latency must be <= 100 ms for acceptable quality, got $maxVoiceLatencyMs ms"
        }
        require(minVideoThroughputMbps > 0) {
            "Min video throughput must be positive, got $minVideoThroughputMbps Mbps"
        }

        // Validate priority ordering
        if (enableWmm) {
            require(voicePriority >= videoPriority) {
                "Voice priority ($voicePriority) should be >= video priority ($videoPriority)"
            }
            require(videoPriority >= dataPriority) {
                "Video priority ($videoPriority) should be >= data priority ($dataPriority)"
            }
            require(dataPriority >= backgroundPriority) {
                "Data priority ($dataPriority) should be >= background priority ($backgroundPriority)"
            }
        }
    }

    /**
     * Whether priorities follow strict WMM hierarchy
     * (Voice > Video > Data > Background)
     */
    val hasStrictHierarchy: Boolean
        get() = voicePriority > videoPriority &&
                videoPriority > dataPriority &&
                dataPriority > backgroundPriority

    /**
     * Whether voice traffic has highest priority
     */
    val voiceIsHighestPriority: Boolean
        get() = voicePriority >= maxOf(videoPriority, dataPriority, backgroundPriority)

    /**
     * Airtime fairness strictness level
     */
    val fairnessStrictness: FairnessStrictness
        get() = when {
            airtimeFairnessThreshold <= 0.2 -> FairnessStrictness.VERY_STRICT
            airtimeFairnessThreshold <= 0.3 -> FairnessStrictness.STRICT
            airtimeFairnessThreshold <= 0.5 -> FairnessStrictness.MODERATE
            else -> FairnessStrictness.PERMISSIVE
        }

    /**
     * Voice quality requirements tier
     */
    val voiceQualityTier: VoiceQualityTier
        get() = when {
            maxVoiceLatencyMs <= 10 -> VoiceQualityTier.PREMIUM
            maxVoiceLatencyMs <= 20 -> VoiceQualityTier.STANDARD
            maxVoiceLatencyMs <= 40 -> VoiceQualityTier.ACCEPTABLE
            else -> VoiceQualityTier.BASIC
        }

    /**
     * Get human-readable summary of QoS goals
     */
    val summary: String
        get() = buildString {
            append("WMM: ${if (enableWmm) "enabled" else "disabled"}, ")
            append("Priorities: V$voicePriority/Vi$videoPriority/D$dataPriority/B$backgroundPriority, ")
            append("Fairness: <${(airtimeFairnessThreshold * 100).toInt()}%, ")
            append("Voice latency: <${maxVoiceLatencyMs}ms")
        }

    companion object {
        /**
         * Default QoS goals for general-purpose networks
         * - WMM enabled with standard priorities
         * - Moderate fairness requirements
         * - Standard voice quality
         */
        fun standard(): QosGoals {
            return QosGoals(
                enableWmm = true,
                voicePriority = 3,
                videoPriority = 2,
                dataPriority = 1,
                backgroundPriority = 0,
                airtimeFairnessThreshold = 0.4,
                maxVoiceLatencyMs = 20,
                minVideoThroughputMbps = 10,
                allowAirtimeHogs = false
            )
        }

        /**
         * QoS goals for enterprise/business networks
         * - Strict WMM hierarchy
         * - Strict fairness enforcement
         * - High voice quality requirements
         */
        fun enterprise(): QosGoals {
            return QosGoals(
                enableWmm = true,
                voicePriority = 3,
                videoPriority = 2,
                dataPriority = 1,
                backgroundPriority = 0,
                airtimeFairnessThreshold = 0.3,
                maxVoiceLatencyMs = 15,
                minVideoThroughputMbps = 15,
                allowAirtimeHogs = false
            )
        }

        /**
         * QoS goals for VoIP-centric deployments
         * - Voice has maximum priority
         * - Very low latency requirements
         * - Strict airtime management
         */
        fun voipCentric(): QosGoals {
            return QosGoals(
                enableWmm = true,
                voicePriority = 3,
                videoPriority = 2,
                dataPriority = 1,
                backgroundPriority = 0,
                airtimeFairnessThreshold = 0.25,
                maxVoiceLatencyMs = 10,
                minVideoThroughputMbps = 5,
                allowAirtimeHogs = false
            )
        }

        /**
         * QoS goals for video-centric deployments (streaming, conferencing)
         * - High video priority
         * - High throughput requirements
         * - Moderate fairness
         */
        fun videoCentric(): QosGoals {
            return QosGoals(
                enableWmm = true,
                voicePriority = 3,
                videoPriority = 3,  // Same as voice
                dataPriority = 1,
                backgroundPriority = 0,
                airtimeFairnessThreshold = 0.35,
                maxVoiceLatencyMs = 20,
                minVideoThroughputMbps = 25,
                allowAirtimeHogs = false
            )
        }

        /**
         * QoS goals for best-effort networks (minimal QoS)
         * - Equal priorities for all traffic
         * - Permissive fairness
         * - No strict requirements
         */
        fun bestEffort(): QosGoals {
            return QosGoals(
                enableWmm = false,
                voicePriority = 1,
                videoPriority = 1,
                dataPriority = 1,
                backgroundPriority = 1,
                airtimeFairnessThreshold = 0.6,
                maxVoiceLatencyMs = 50,
                minVideoThroughputMbps = 5,
                allowAirtimeHogs = true
            )
        }
    }
}

/**
 * Airtime fairness strictness levels
 *
 * Gini coefficient measures inequality in airtime distribution:
 * - 0.0 = Perfect equality (all clients get equal airtime)
 * - 1.0 = Perfect inequality (one client gets all airtime)
 */
enum class FairnessStrictness(
    val displayName: String,
    val description: String,
    val giniThreshold: Double
) {
    /**
     * Very strict fairness (Gini <= 0.2)
     * - Near-perfect airtime equality
     * - May impact performance for legitimate heavy users
     * - Suitable for public WiFi, hotspots
     */
    VERY_STRICT(
        "Very Strict",
        "Near-perfect airtime equality enforced",
        0.2
    ),

    /**
     * Strict fairness (Gini <= 0.3)
     * - Strong fairness enforcement
     * - Good for shared environments
     * - Prevents individual client dominance
     */
    STRICT(
        "Strict",
        "Strong fairness enforcement",
        0.3
    ),

    /**
     * Moderate fairness (Gini <= 0.5)
     * - Balanced approach
     * - Allows some variation in usage
     * - Suitable for most deployments
     */
    MODERATE(
        "Moderate",
        "Balanced fairness with usage variation allowed",
        0.5
    ),

    /**
     * Permissive fairness (Gini > 0.5)
     * - Minimal enforcement
     * - Heavy users allowed to dominate
     * - May cause performance issues for light users
     */
    PERMISSIVE(
        "Permissive",
        "Minimal fairness enforcement",
        1.0
    )
}

/**
 * Voice quality requirement tiers
 *
 * Based on ITU-T G.114 recommendations for one-way latency:
 * - < 150 ms: Acceptable for most applications
 * - < 100 ms: Acceptable for most interactive applications
 * - < 50 ms: Recommended for high-quality interactive voice
 */
enum class VoiceQualityTier(
    val displayName: String,
    val description: String,
    val maxLatencyMs: Int
) {
    /**
     * Premium voice quality (< 10 ms WiFi latency)
     * - Professional VoIP
     * - HD audio conferencing
     * - Mission-critical communications
     */
    PREMIUM(
        "Premium",
        "Professional-grade voice quality",
        10
    ),

    /**
     * Standard voice quality (< 20 ms WiFi latency)
     * - Standard VoIP
     * - Video conferencing
     * - Suitable for business use
     */
    STANDARD(
        "Standard",
        "Standard VoIP quality",
        20
    ),

    /**
     * Acceptable voice quality (< 40 ms WiFi latency)
     * - Basic VoIP
     * - Casual voice calls
     * - May have occasional quality issues
     */
    ACCEPTABLE(
        "Acceptable",
        "Acceptable for basic voice calls",
        40
    ),

    /**
     * Basic voice quality (< 100 ms WiFi latency)
     * - Minimal quality
     * - Noticeable latency
     * - Not recommended for professional use
     */
    BASIC(
        "Basic",
        "Basic quality with noticeable latency",
        100
    )
}

/**
 * WMM Access Categories (IEEE 802.11e)
 *
 * Each category has different channel access parameters:
 * - AIFSN (Arbitration Inter-Frame Space Number)
 * - CWmin/CWmax (Contention Window)
 * - TXOP (Transmission Opportunity)
 */
enum class WmmAccessCategory(
    val displayName: String,
    val priority: Int,
    val description: String
) {
    /**
     * Voice (AC_VO) - Highest priority
     * - VoIP, real-time voice
     * - Minimum latency
     * - Small frames
     */
    VOICE("Voice (AC_VO)", 3, "VoIP and real-time voice"),

    /**
     * Video (AC_VI) - High priority
     * - Video streaming, conferencing
     * - Low latency and jitter
     * - Sustained throughput
     */
    VIDEO("Video (AC_VI)", 2, "Video streaming and conferencing"),

    /**
     * Best Effort (AC_BE) - Normal priority
     * - General data traffic
     * - Web browsing, file transfers
     * - Default category
     */
    BEST_EFFORT("Best Effort (AC_BE)", 1, "General data traffic"),

    /**
     * Background (AC_BK) - Lowest priority
     * - Background transfers
     * - System updates, backups
     * - Non-time-sensitive
     */
    BACKGROUND("Background (AC_BK)", 0, "Background and bulk transfers")
}
