package io.lamco.netkit.diagnostics.integration

import io.lamco.netkit.diagnostics.model.ActiveDiagnostics

/**
 * Comprehensive network health scoring system.
 *
 * NetworkHealthScore combines passive analysis (RF metrics, topology, security) with
 * active diagnostics (ping, traceroute, bandwidth, DNS) to provide a holistic
 * assessment of network quality and performance.
 *
 * ## Scoring Dimensions
 *
 * 1. **RF Quality** (25%): Signal strength, SNR, interference
 * 2. **Connectivity** (25%): Ping latency, packet loss, jitter
 * 3. **Throughput** (20%): Bandwidth test results
 * 4. **Routing** (10%): Traceroute hop count, gateway health
 * 5. **DNS Performance** (10%): Resolution speed and reliability
 * 6. **Security Posture** (10%): Security configuration and vulnerabilities
 *
 * ## Health Categories
 *
 * - **EXCELLENT** (90-100): All metrics optimal, no issues
 * - **GOOD** (75-89): Minor issues, generally healthy
 * - **FAIR** (60-74): Moderate issues, acceptable performance
 * - **POOR** (40-59): Significant issues, degraded performance
 * - **CRITICAL** (0-39): Severe issues, immediate attention required
 *
 * ## Usage Example
 *
 * ```kotlin
 * val healthScore = NetworkHealthScore.calculate(
 *     rfQuality = 85.0,
 *     connectivityScore = 90.0,
 *     throughputScore = 75.0,
 *     routingScore = 80.0,
 *     dnsScore = 88.0,
 *     securityScore = 70.0
 * )
 *
 * println("Overall Health: ${healthScore.overallHealth}")
 * println("Score: ${healthScore.overallScore}/100")
 * println("Primary Concern: ${healthScore.primaryConcern}")
 * ```
 *
 * @property overallScore Overall health score (0-100)
 * @property overallHealth Health category
 * @property rfQualityScore RF quality score (0-100)
 * @property connectivityScore Connectivity score (0-100)
 * @property throughputScore Throughput score (0-100)
 * @property routingScore Routing score (0-100)
 * @property dnsScore DNS performance score (0-100)
 * @property securityScore Security posture score (0-100)
 * @property strengths List of network strengths
 * @property weaknesses List of network weaknesses
 * @property primaryConcern Most critical issue (if any)
 */
data class NetworkHealthScore(
    val overallScore: Double,
    val overallHealth: NetworkHealth,
    val rfQualityScore: Double,
    val connectivityScore: Double,
    val throughputScore: Double,
    val routingScore: Double,
    val dnsScore: Double,
    val securityScore: Double,
    val strengths: List<HealthDimension> = emptyList(),
    val weaknesses: List<HealthDimension> = emptyList(),
    val primaryConcern: String? = null,
) {
    init {
        require(overallScore in 0.0..100.0) { "Overall score must be 0-100, got $overallScore" }
        require(rfQualityScore in 0.0..100.0) { "RF quality score must be 0-100" }
        require(connectivityScore in 0.0..100.0) { "Connectivity score must be 0-100" }
        require(throughputScore in 0.0..100.0) { "Throughput score must be 0-100" }
        require(routingScore in 0.0..100.0) { "Routing score must be 0-100" }
        require(dnsScore in 0.0..100.0) { "DNS score must be 0-100" }
        require(securityScore in 0.0..100.0) { "Security score must be 0-100" }
    }

    /**
     * Whether the network is healthy enough for general use.
     */
    val isHealthy: Boolean = overallScore >= 60.0

    /**
     * Whether immediate action is required.
     */
    val requiresImmediateAction: Boolean = overallHealth == NetworkHealth.CRITICAL

    /**
     * Get all dimension scores as a map.
     */
    fun dimensionScores(): Map<String, Double> =
        mapOf(
            "RF Quality" to rfQualityScore,
            "Connectivity" to connectivityScore,
            "Throughput" to throughputScore,
            "Routing" to routingScore,
            "DNS" to dnsScore,
            "Security" to securityScore,
        )

    /**
     * Get the lowest scoring dimension.
     * Excludes dimensions with score 0.0 (assumed not measured).
     */
    fun weakestDimension(): Pair<String, Double> {
        val measured = dimensionScores().filter { it.value > 0.0 }
        val weakest = measured.minByOrNull { it.value }
        return if (weakest != null) {
            weakest.key to weakest.value
        } else {
            "Unknown" to 0.0
        }
    }

    /**
     * Get the highest scoring dimension.
     */
    fun strongestDimension(): Pair<String, Double> {
        val strongest = dimensionScores().maxByOrNull { it.value }
        return if (strongest != null) {
            strongest.key to strongest.value
        } else {
            "Unknown" to 0.0
        }
    }

    /**
     * Human-readable summary of network health.
     */
    fun summary(): String =
        buildString {
            appendLine("Network Health Assessment")
            appendLine("=".repeat(50))
            appendLine("Overall Score: ${"%.1f".format(overallScore)}/100 ($overallHealth)")
            appendLine()
            appendLine("Dimension Scores:")
            appendLine("  RF Quality:    ${"%.1f".format(rfQualityScore)}/100")
            appendLine("  Connectivity:  ${"%.1f".format(connectivityScore)}/100")
            appendLine("  Throughput:    ${"%.1f".format(throughputScore)}/100")
            appendLine("  Routing:       ${"%.1f".format(routingScore)}/100")
            appendLine("  DNS:           ${"%.1f".format(dnsScore)}/100")
            appendLine("  Security:      ${"%.1f".format(securityScore)}/100")
            appendLine()

            if (strengths.isNotEmpty()) {
                appendLine("Strengths:")
                strengths.forEach { appendLine("  ✓ ${it.description}") }
                appendLine()
            }

            if (weaknesses.isNotEmpty()) {
                appendLine("Weaknesses:")
                weaknesses.forEach { appendLine("  ✗ ${it.description}") }
                appendLine()
            }

            if (primaryConcern != null) {
                appendLine("Primary Concern: $primaryConcern")
            }
        }

    companion object {
        /** Weight for RF quality in overall score */
        const val RF_WEIGHT = 0.25

        /** Weight for connectivity in overall score */
        const val CONNECTIVITY_WEIGHT = 0.25

        /** Weight for throughput in overall score */
        const val THROUGHPUT_WEIGHT = 0.20

        /** Weight for routing in overall score */
        const val ROUTING_WEIGHT = 0.10

        /** Weight for DNS in overall score */
        const val DNS_WEIGHT = 0.10

        /** Weight for security in overall score */
        const val SECURITY_WEIGHT = 0.10

        /**
         * Calculate network health score from individual dimension scores.
         *
         * @param rfQuality RF quality score (0-100), null if not available
         * @param connectivityScore Connectivity score (0-100), null if not available
         * @param throughputScore Throughput score (0-100), null if not available
         * @param routingScore Routing score (0-100), null if not available
         * @param dnsScore DNS score (0-100), null if not available
         * @param securityScore Security score (0-100), null if not available
         * @return NetworkHealthScore with weighted overall score
         */
        fun calculate(
            rfQuality: Double? = null,
            connectivityScore: Double? = null,
            throughputScore: Double? = null,
            routingScore: Double? = null,
            dnsScore: Double? = null,
            securityScore: Double? = null,
        ): NetworkHealthScore {
            // Build dimension map for data-driven processing
            val dimensions =
                buildDimensionMap(
                    rfQuality,
                    connectivityScore,
                    throughputScore,
                    routingScore,
                    dnsScore,
                    securityScore,
                )

            val overallScore = calculateWeightedScore(dimensions)
            val health = categorizeHealth(overallScore)
            val strengths = identifyStrengths(dimensions, threshold = 85.0)
            val weaknesses = identifyWeaknesses(dimensions, threshold = 60.0)
            val primaryConcern = findPrimaryConcern(dimensions, weaknesses)

            return NetworkHealthScore(
                overallScore = overallScore,
                overallHealth = health,
                rfQualityScore = rfQuality ?: 0.0,
                connectivityScore = connectivityScore ?: 0.0,
                throughputScore = throughputScore ?: 0.0,
                routingScore = routingScore ?: 0.0,
                dnsScore = dnsScore ?: 0.0,
                securityScore = securityScore ?: 0.0,
                strengths = strengths,
                weaknesses = weaknesses,
                primaryConcern = primaryConcern,
            )
        }

        /**
         * Build dimension map with scores and metadata.
         */
        private fun buildDimensionMap(
            rfQuality: Double?,
            connectivityScore: Double?,
            throughputScore: Double?,
            routingScore: Double?,
            dnsScore: Double?,
            securityScore: Double?,
        ): Map<HealthDimension, DimensionData> =
            buildMap {
                rfQuality?.let {
                    put(HealthDimension.RF_QUALITY, DimensionData(it, RF_WEIGHT, "RF Quality"))
                }
                connectivityScore?.let {
                    put(HealthDimension.CONNECTIVITY, DimensionData(it, CONNECTIVITY_WEIGHT, "Connectivity"))
                }
                throughputScore?.let {
                    put(HealthDimension.THROUGHPUT, DimensionData(it, THROUGHPUT_WEIGHT, "Throughput"))
                }
                routingScore?.let {
                    put(HealthDimension.ROUTING, DimensionData(it, ROUTING_WEIGHT, "Routing"))
                }
                dnsScore?.let {
                    put(HealthDimension.DNS, DimensionData(it, DNS_WEIGHT, "DNS"))
                }
                securityScore?.let {
                    put(HealthDimension.SECURITY, DimensionData(it, SECURITY_WEIGHT, "Security"))
                }
            }

        /**
         * Calculate weighted overall score.
         */
        private fun calculateWeightedScore(dimensions: Map<HealthDimension, DimensionData>): Double {
            val totalWeight = dimensions.values.sumOf { it.weight }
            return if (totalWeight > 0.0) {
                val raw = dimensions.values.sumOf { it.score * it.weight } / totalWeight
                // Round to avoid floating-point precision issues
                (raw * 100.0).toLong() / 100.0
            } else {
                0.0
            }
        }

        /**
         * Categorize health based on overall score.
         */
        private fun categorizeHealth(score: Double): NetworkHealth =
            when {
                score >= 90.0 -> NetworkHealth.EXCELLENT
                score >= 75.0 -> NetworkHealth.GOOD
                score >= 60.0 -> NetworkHealth.FAIR
                score >= 40.0 -> NetworkHealth.POOR
                else -> NetworkHealth.CRITICAL
            }

        /**
         * Identify dimensions above threshold (strengths).
         */
        private fun identifyStrengths(
            dimensions: Map<HealthDimension, DimensionData>,
            threshold: Double,
        ): List<HealthDimension> =
            dimensions
                .filter { it.value.score >= threshold }
                .keys
                .toList()

        /**
         * Identify dimensions below threshold (weaknesses).
         */
        private fun identifyWeaknesses(
            dimensions: Map<HealthDimension, DimensionData>,
            threshold: Double,
        ): List<HealthDimension> =
            dimensions
                .filter { it.value.score < threshold }
                .keys
                .toList()

        /**
         * Find primary concern (lowest scoring dimension if any weaknesses).
         */
        private fun findPrimaryConcern(
            dimensions: Map<HealthDimension, DimensionData>,
            weaknesses: List<HealthDimension>,
        ): String? {
            if (weaknesses.isEmpty()) return null

            val lowestDimension = dimensions.minByOrNull { it.value.score } ?: return null
            val data = lowestDimension.value
            return "${data.displayName} (${data.score.toInt()}/100)"
        }

        /**
         * Dimension data holder.
         */
        private data class DimensionData(
            val score: Double,
            val weight: Double,
            val displayName: String,
        )

        /**
         * Calculate network health from active diagnostics only.
         *
         * @param diagnostics Active diagnostic results
         * @return NetworkHealthScore based on diagnostic data
         */
        fun fromActiveDiagnostics(diagnostics: ActiveDiagnostics): NetworkHealthScore {
            val connectivityScore = diagnostics.connectivityQuality.toScore().toDouble()
            val routingScore = diagnostics.routingQuality.toScore().toDouble()
            val throughputScore = diagnostics.throughputQuality.toScore()
            val dnsScore = diagnostics.dnsQuality.toScore().toDouble()

            return calculate(
                connectivityScore = connectivityScore,
                throughputScore = throughputScore.toDouble(),
                routingScore = routingScore,
                dnsScore = dnsScore,
            )
        }
    }
}

/**
 * Network health categories.
 */
enum class NetworkHealth {
    /** 90-100: Excellent network health */
    EXCELLENT,

    /** 75-89: Good network health */
    GOOD,

    /** 60-74: Fair network health */
    FAIR,

    /** 40-59: Poor network health */
    POOR,

    /** 0-39: Critical network issues */
    CRITICAL,

    ;

    /**
     * Human-readable description.
     */
    val description: String
        get() =
            when (this) {
                EXCELLENT -> "Excellent network health - all systems optimal"
                GOOD -> "Good network health - minor issues if any"
                FAIR -> "Fair network health - acceptable performance"
                POOR -> "Poor network health - significant issues"
                CRITICAL -> "Critical network issues - immediate attention required"
            }

    /**
     * Emoji indicator for visual representation.
     */
    val emoji: String
        get() =
            when (this) {
                EXCELLENT -> "🟢"
                GOOD -> "🟡"
                FAIR -> "🟠"
                POOR -> "🔴"
                CRITICAL -> "⛔"
            }
}

/**
 * Network health dimensions.
 */
enum class HealthDimension {
    RF_QUALITY,
    CONNECTIVITY,
    THROUGHPUT,
    ROUTING,
    DNS,
    SECURITY,
    ;

    /**
     * Human-readable description.
     */
    val description: String
        get() =
            when (this) {
                RF_QUALITY -> "Strong RF signal quality"
                CONNECTIVITY -> "Reliable connectivity"
                THROUGHPUT -> "Good bandwidth/throughput"
                ROUTING -> "Efficient network routing"
                DNS -> "Fast DNS resolution"
                SECURITY -> "Strong security posture"
            }
}

/**
 * Helper to convert quality enums to 0-100 scores.
 */
private fun io.lamco.netkit.diagnostics.model.PingQuality.toScore(): Int =
    when (this) {
        io.lamco.netkit.diagnostics.model.PingQuality.EXCELLENT -> 100
        io.lamco.netkit.diagnostics.model.PingQuality.GOOD -> 80
        io.lamco.netkit.diagnostics.model.PingQuality.FAIR -> 60
        io.lamco.netkit.diagnostics.model.PingQuality.POOR -> 40
        io.lamco.netkit.diagnostics.model.PingQuality.CRITICAL -> 20
        io.lamco.netkit.diagnostics.model.PingQuality.FAILED -> 0
    }

private fun io.lamco.netkit.diagnostics.model.RouteQuality.toScore(): Int =
    when (this) {
        io.lamco.netkit.diagnostics.model.RouteQuality.EXCELLENT -> 100
        io.lamco.netkit.diagnostics.model.RouteQuality.GOOD -> 80
        io.lamco.netkit.diagnostics.model.RouteQuality.FAIR -> 60
        io.lamco.netkit.diagnostics.model.RouteQuality.POOR -> 40
        io.lamco.netkit.diagnostics.model.RouteQuality.CRITICAL -> 20
        io.lamco.netkit.diagnostics.model.RouteQuality.FAILED -> 0
    }

private fun io.lamco.netkit.diagnostics.model.DnsResolutionQuality.toScore(): Int =
    when (this) {
        io.lamco.netkit.diagnostics.model.DnsResolutionQuality.EXCELLENT -> 100
        io.lamco.netkit.diagnostics.model.DnsResolutionQuality.GOOD -> 80
        io.lamco.netkit.diagnostics.model.DnsResolutionQuality.FAIR -> 60
        io.lamco.netkit.diagnostics.model.DnsResolutionQuality.POOR -> 40
        io.lamco.netkit.diagnostics.model.DnsResolutionQuality.CRITICAL -> 20
        io.lamco.netkit.diagnostics.model.DnsResolutionQuality.FAILED -> 0
    }
