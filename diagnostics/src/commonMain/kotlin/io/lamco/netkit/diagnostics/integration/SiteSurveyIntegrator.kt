package io.lamco.netkit.diagnostics.integration

import io.lamco.netkit.diagnostics.model.*
import kotlin.math.abs

/**
 * Integrates passive site survey data with active diagnostic results.
 *
 * SiteSurveyIntegrator provides comprehensive integration of:
 * - **Passive RF metrics**: Signal strength, SNR, interference, channel utilization
 * - **Topology analysis**: Multi-AP detection, roaming performance, mesh networks
 * - **Security analysis**: Encryption strength, vulnerabilities, attack surface
 * - **Active diagnostics**: Ping, traceroute, bandwidth, DNS performance
 *
 * ## Advanced Correlation Analysis
 *
 * The integrator uses a sophisticated correlation engine that:
 * 1. Identifies causal relationships between metrics
 * 2. Detects anomalies and unexpected patterns
 * 3. Quantifies correlation confidence and impact
 * 4. Provides multi-dimensional root cause analysis
 * 5. Generates context-aware recommendations
 *
 * ## Correlation Types
 *
 * - **CAUSAL**: Direct cause-effect relationship (e.g., poor RF → high latency)
 * - **SYMPTOMATIC**: Indicators of underlying issue (e.g., DNS slow + routing slow → ISP issue)
 * - **RELATED**: Associated but not directly causal (e.g., security weak + performance good)
 * - **CONFLICTING**: Contradictory signals requiring investigation
 *
 * ## Usage Example
 *
 * ```kotlin
 * val integrator = SiteSurveyIntegrator(
 *     correlationThreshold = 0.7,
 *     enableAnomalyDetection = true
 * )
 *
 * val integrated = integrator.integrate(
 *     activeDiagnostics = diagnostics,
 *     rfQuality = 85.0,
 *     topologyQuality = 90.0,
 *     securityScore = 70.0,
 *     location = "Building A - Floor 2"
 * )
 *
 * // Access results
 * println("Health Score: ${integrated.healthScore.overallScore}")
 * println("High-confidence correlations: ${integrated.highConfidenceCorrelations.size}")
 * println("Anomalies detected: ${integrated.anomalies.size}")
 * ```
 *
 * @property correlationThreshold Minimum confidence for reporting correlations (0.0-1.0)
 * @property enableAnomalyDetection Whether to detect statistical anomalies
 * @property enableMultiFactorAnalysis Whether to analyze multi-dimensional correlations
 *
 * @see IntegratedAnalysis
 * @see Correlation
 * @see CorrelationType
 */
class SiteSurveyIntegrator(
    private val correlationThreshold: Double = 0.6,
    private val enableAnomalyDetection: Boolean = true,
    private val enableMultiFactorAnalysis: Boolean = true,
) {
    init {
        require(correlationThreshold in 0.0..1.0) {
            "Correlation threshold must be 0.0-1.0, got $correlationThreshold"
        }
    }

    /**
     * Integrate passive and active analysis with comprehensive correlation.
     *
     * @param activeDiagnostics Active diagnostic test results
     * @param rfQuality RF quality score (0-100), null if not available
     * @param topologyQuality Topology quality score (0-100), null if not available
     * @param securityScore Security posture score (0-100), null if not available
     * @param location Optional location/site identifier
     * @param timestamp When the analysis was performed
     * @return Integrated analysis combining all data sources
     */
    fun integrate(
        activeDiagnostics: ActiveDiagnostics,
        rfQuality: Double? = null,
        topologyQuality: Double? = null,
        securityScore: Double? = null,
        location: String? = null,
        timestamp: Long = System.currentTimeMillis(),
    ): IntegratedAnalysis {
        val healthScore =
            NetworkHealthScore.calculate(
                rfQuality = rfQuality,
                connectivityScore = activeDiagnostics.connectivityQuality.toScore().toDouble(),
                throughputScore = activeDiagnostics.throughputQuality.toScore().toDouble(),
                routingScore = activeDiagnostics.routingQuality.toScore().toDouble(),
                dnsScore = activeDiagnostics.dnsQuality.toScore().toDouble(),
                securityScore = securityScore,
            )

        val advisor = DiagnosticAdvisor()
        val recommendations = advisor.analyzeAndRecommend(activeDiagnostics, healthScore)

        val context =
            CorrelationContext(
                activeDiagnostics = activeDiagnostics,
                rfQuality = rfQuality,
                topologyQuality = topologyQuality,
                securityScore = securityScore,
                healthScore = healthScore,
            )

        val allCorrelations = mutableListOf<Correlation>()
        allCorrelations.addAll(analyzeRfCorrelations(context))
        allCorrelations.addAll(analyzeLatencyCorrelations(context))
        allCorrelations.addAll(analyzeBandwidthCorrelations(context))
        allCorrelations.addAll(analyzeDnsCorrelations(context))
        allCorrelations.addAll(analyzeRoutingCorrelations(context))
        allCorrelations.addAll(analyzeSecurityCorrelations(context))
        allCorrelations.addAll(analyzeTopologyCorrelations(context))

        if (enableMultiFactorAnalysis) {
            allCorrelations.addAll(analyzeMultiFactorCorrelations(context))
        }

        val filteredCorrelations = allCorrelations.filter { it.confidence >= correlationThreshold }

        val anomalies =
            if (enableAnomalyDetection) {
                detectAnomalies(context)
            } else {
                emptyList()
            }

        return IntegratedAnalysis(
            healthScore = healthScore,
            activeDiagnostics = activeDiagnostics,
            recommendations = recommendations,
            correlations = filteredCorrelations,
            anomalies = anomalies,
            location = location,
            timestamp = timestamp,
            rfQualityScore = rfQuality,
            topologyQualityScore = topologyQuality,
            securityScore = securityScore,
        )
    }

    // RF-based correlation analysis

    private fun analyzeRfCorrelations(context: CorrelationContext): List<Correlation> {
        val correlations = mutableListOf<Correlation>()
        val rfQuality = context.rfQuality ?: return correlations
        val diag = context.activeDiagnostics

        // Low RF + High packet loss = RF issue (CAUSAL)
        if (rfQuality < 60.0 && diag.worstPacketLoss >= 5.0) {
            val recommendation =
                "Improve RF coverage: move closer to AP, remove obstacles, add access points, " +
                    "or switch to less congested channel"
            correlations.add(
                Correlation(
                    finding = "Packet loss (${diag.worstPacketLoss}%) correlates with poor RF quality (${rfQuality.toInt()}/100)",
                    rootCause = "Weak WiFi signal or RF interference causing packet drops",
                    recommendation = recommendation,
                    type = CorrelationType.CAUSAL,
                    confidence = calculateConfidence(rfQuality, diag.worstPacketLoss, inverse = true),
                    severity = CorrelationSeverity.HIGH,
                    category = CorrelationCategory.RF_CONNECTIVITY,
                ),
            )
        }

        // Very low RF + Critical latency = Severe RF issue
        val bestLatencyMs = diag.bestLatency?.inWholeMilliseconds?.toDouble()
        if (rfQuality < 40.0 && bestLatencyMs != null && bestLatencyMs > 100.0) {
            correlations.add(
                Correlation(
                    finding = "Critical latency (${bestLatencyMs.toInt()}ms) with very poor RF (${rfQuality.toInt()}/100)",
                    rootCause = "Severe RF signal degradation causing connectivity breakdown",
                    recommendation = "URGENT: Improve RF immediately - network is barely functional. Consider wired connection.",
                    type = CorrelationType.CAUSAL,
                    confidence = 0.95,
                    severity = CorrelationSeverity.CRITICAL,
                    category = CorrelationCategory.RF_CONNECTIVITY,
                ),
            )
        }

        // Moderate RF + Intermittent issues = Interference
        if (rfQuality in 50.0..70.0 && diag.worstPacketLoss in 2.0..10.0) {
            correlations.add(
                Correlation(
                    finding = "Moderate RF quality (${rfQuality.toInt()}/100) with intermittent packet loss (${diag.worstPacketLoss}%)",
                    rootCause = "Likely RF interference from neighboring networks or devices",
                    recommendation = "Analyze RF spectrum, switch to 5GHz band if on 2.4GHz, or choose cleaner channel",
                    type = CorrelationType.SYMPTOMATIC,
                    confidence = 0.7,
                    severity = CorrelationSeverity.MEDIUM,
                    category = CorrelationCategory.RF_INTERFERENCE,
                ),
            )
        }

        // Good RF + Low jitter = Stable RF environment
        val bestJitterMs =
            diag.pingTests
                .filter {
                    it.isSuccessful
                }.mapNotNull { it.jitter?.inWholeMilliseconds?.toDouble() }
                .minOrNull()
        if (rfQuality >= 80.0 && bestJitterMs != null && bestJitterMs < 5.0) {
            correlations.add(
                Correlation(
                    finding = "Excellent RF quality (${rfQuality.toInt()}/100) with low jitter (${bestJitterMs.toInt()}ms)",
                    rootCause = "Clean RF environment with minimal interference",
                    recommendation = "RF is optimal - no changes needed to wireless layer",
                    type = CorrelationType.RELATED,
                    confidence = 0.85,
                    severity = CorrelationSeverity.INFORMATIONAL,
                    category = CorrelationCategory.RF_CONNECTIVITY,
                ),
            )
        }

        return correlations
    }

    // Latency correlation analysis

    private fun analyzeLatencyCorrelations(context: CorrelationContext): List<Correlation> {
        val correlations = mutableListOf<Correlation>()
        val diag = context.activeDiagnostics
        val avgLatencyMs = diag.bestLatency?.inWholeMilliseconds?.toDouble() ?: return correlations

        // High latency + High jitter = Unstable connection
        val jitterMs =
            diag.pingTests
                .filter {
                    it.isSuccessful
                }.mapNotNull { it.jitter?.inWholeMilliseconds?.toDouble() }
                .minOrNull()
        if (avgLatencyMs > 50.0 && jitterMs != null && jitterMs > 10.0) {
            correlations.add(
                Correlation(
                    finding = "High latency (${avgLatencyMs.toInt()}ms) with high jitter (${jitterMs.toInt()}ms)",
                    rootCause = "Unstable network path - likely congestion or variable routing",
                    recommendation = "Check for network congestion, QoS settings, or ISP routing issues",
                    type = CorrelationType.SYMPTOMATIC,
                    confidence = 0.8,
                    severity = CorrelationSeverity.MEDIUM,
                    category = CorrelationCategory.LATENCY_ROUTING,
                ),
            )
        }

        // Good RF but high latency = Non-RF issue
        val rfQuality = context.rfQuality
        if (rfQuality != null && rfQuality >= 75.0 && avgLatencyMs > 100.0) {
            correlations.add(
                Correlation(
                    finding = "High latency (${avgLatencyMs.toInt()}ms) despite good RF (${rfQuality.toInt()}/100)",
                    rootCause = "Latency source is beyond WiFi - likely ISP, routing, or WAN issue",
                    recommendation = "Test with wired connection, check ISP status, trace route to identify hop delays",
                    type = CorrelationType.CAUSAL,
                    confidence = 0.85,
                    severity = CorrelationSeverity.MEDIUM,
                    category = CorrelationCategory.LATENCY_ROUTING,
                ),
            )
        }

        // Low latency + Low packet loss = Healthy connectivity
        if (avgLatencyMs < 20.0 && diag.worstPacketLoss < 1.0) {
            correlations.add(
                Correlation(
                    finding = "Excellent latency (${avgLatencyMs.toInt()}ms) with minimal packet loss (${diag.worstPacketLoss}%)",
                    rootCause = "Optimal network path with low congestion",
                    recommendation = "Connectivity is excellent - maintain current configuration",
                    type = CorrelationType.RELATED,
                    confidence = 0.9,
                    severity = CorrelationSeverity.INFORMATIONAL,
                    category = CorrelationCategory.LATENCY_ROUTING,
                ),
            )
        }

        return correlations
    }

    // Bandwidth correlation analysis

    private fun analyzeBandwidthCorrelations(context: CorrelationContext): List<Correlation> {
        val correlations = mutableListOf<Correlation>()
        val diag = context.activeDiagnostics
        val downloadSpeed = diag.bestDownloadSpeed ?: return correlations
        val rfQuality = context.rfQuality

        // Good RF + Poor throughput = ISP or congestion
        if (rfQuality != null && rfQuality >= 80.0 && downloadSpeed < 25.0) {
            correlations.add(
                Correlation(
                    finding = "Low bandwidth ($downloadSpeed Mbps) despite excellent RF (${rfQuality.toInt()}/100)",
                    rootCause = "Throughput bottleneck is not WiFi - likely ISP speed limit or network congestion",
                    recommendation = "Verify ISP plan speed, test with ethernet, check for bandwidth-heavy devices or background traffic",
                    type = CorrelationType.CAUSAL,
                    confidence = 0.9,
                    severity = CorrelationSeverity.HIGH,
                    category = CorrelationCategory.BANDWIDTH_ISP,
                ),
            )
        }

        // Poor RF + Poor throughput = RF limited
        if (rfQuality != null && rfQuality < 60.0 && downloadSpeed < 50.0) {
            correlations.add(
                Correlation(
                    finding = "Low bandwidth ($downloadSpeed Mbps) with poor RF (${rfQuality.toInt()}/100)",
                    rootCause = "WiFi RF quality is limiting throughput capacity",
                    recommendation = "Improve RF first: signal strength, interference reduction, channel optimization",
                    type = CorrelationType.CAUSAL,
                    confidence = 0.85,
                    severity = CorrelationSeverity.HIGH,
                    category = CorrelationCategory.BANDWIDTH_RF,
                ),
            )
        }

        // Asymmetric bandwidth = ISP plan or config
        val uploadSpeed = diag.bestUploadSpeed
        if (uploadSpeed != null && downloadSpeed > 0 && uploadSpeed > 0) {
            val ratio = downloadSpeed / uploadSpeed
            if (ratio > 10.0 || ratio < 0.1) {
                correlations.add(
                    Correlation(
                        finding = "Highly asymmetric bandwidth (DL: $downloadSpeed Mbps, UL: $uploadSpeed Mbps, ratio: ${"%.1f".format(
                            ratio,
                        )})",
                        rootCause = "ISP plan is asymmetric (common for residential) or upload traffic shaping",
                        recommendation = "Expected for most residential ISPs. Upgrade to business plan if symmetric bandwidth needed.",
                        type = CorrelationType.RELATED,
                        confidence = 0.75,
                        severity = CorrelationSeverity.LOW,
                        category = CorrelationCategory.BANDWIDTH_ISP,
                    ),
                )
            }
        }

        // High bandwidth + Good RF = Optimal performance
        if (rfQuality != null && rfQuality >= 85.0 && downloadSpeed >= 100.0) {
            correlations.add(
                Correlation(
                    finding = "High bandwidth ($downloadSpeed Mbps) with excellent RF (${rfQuality.toInt()}/100)",
                    rootCause = "Optimal RF and ISP performance",
                    recommendation = "Network is performing excellently - no action needed",
                    type = CorrelationType.RELATED,
                    confidence = 0.9,
                    severity = CorrelationSeverity.INFORMATIONAL,
                    category = CorrelationCategory.BANDWIDTH_RF,
                ),
            )
        }

        return correlations
    }

    // DNS correlation analysis

    private fun analyzeDnsCorrelations(context: CorrelationContext): List<Correlation> {
        val correlations = mutableListOf<Correlation>()
        val diag = context.activeDiagnostics

        if (diag.dnsTests.isEmpty()) return correlations

        val avgDnsTime = diag.dnsTests.map { it.resolutionTime.inWholeMilliseconds }.average()
        val maxDnsTime = diag.dnsTests.maxOf { it.resolutionTime.inWholeMilliseconds }

        // Slow DNS + Good connectivity = DNS server issue
        val bestLatencyMs = diag.bestLatency?.inWholeMilliseconds?.toDouble()
        if (avgDnsTime > 50.0 && bestLatencyMs != null && bestLatencyMs < 30.0) {
            correlations.add(
                Correlation(
                    finding = "Slow DNS resolution (${avgDnsTime.toInt()}ms avg) despite good connectivity (${bestLatencyMs.toInt()}ms)",
                    rootCause = "DNS server is slow or overloaded - not a network issue",
                    recommendation = "Switch to faster DNS servers: Google (8.8.8.8), Cloudflare (1.1.1.1), or Quad9 (9.9.9.9)",
                    type = CorrelationType.CAUSAL,
                    confidence = 0.85,
                    severity = CorrelationSeverity.MEDIUM,
                    category = CorrelationCategory.DNS_SERVER,
                ),
            )
        }

        // Variable DNS times = DNS load or routing
        if (maxDnsTime > avgDnsTime * 3) {
            correlations.add(
                Correlation(
                    finding = "Highly variable DNS resolution times (avg: ${avgDnsTime.toInt()}ms, max: ${maxDnsTime.toInt()}ms)",
                    rootCause = "DNS server load variation or unstable routing to DNS",
                    recommendation = "Use multiple DNS servers for redundancy, or switch to more consistent provider",
                    type = CorrelationType.SYMPTOMATIC,
                    confidence = 0.7,
                    severity = CorrelationSeverity.LOW,
                    category = CorrelationCategory.DNS_SERVER,
                ),
            )
        }

        // Fast DNS = Good configuration
        if (avgDnsTime < 20.0) {
            correlations.add(
                Correlation(
                    finding = "Fast DNS resolution (${avgDnsTime.toInt()}ms average)",
                    rootCause = "DNS server is performant and well-configured",
                    recommendation = "DNS configuration is optimal - no changes needed",
                    type = CorrelationType.RELATED,
                    confidence = 0.8,
                    severity = CorrelationSeverity.INFORMATIONAL,
                    category = CorrelationCategory.DNS_SERVER,
                ),
            )
        }

        return correlations
    }

    // Routing correlation analysis

    private fun analyzeRoutingCorrelations(context: CorrelationContext): List<Correlation> {
        val correlations = mutableListOf<Correlation>()
        val diag = context.activeDiagnostics

        if (diag.tracerouteResults.isEmpty()) return correlations

        val maxHops = diag.tracerouteResults.maxOf { it.hops.size }
        val allBottlenecks = diag.tracerouteResults.flatMap { it.findBottlenecks() }
        val hasBottleneck = allBottlenecks.isNotEmpty()

        // Many hops = Long routing path
        if (maxHops > 15) {
            correlations.add(
                Correlation(
                    finding = "Long routing path ($maxHops hops to destination)",
                    rootCause = "Suboptimal ISP routing or geographic distance",
                    recommendation = "Consider CDN, edge servers, or different ISP with better peering if latency is problematic",
                    type = CorrelationType.SYMPTOMATIC,
                    confidence = 0.75,
                    severity = CorrelationSeverity.LOW,
                    category = CorrelationCategory.LATENCY_ROUTING,
                ),
            )
        }

        // Routing bottleneck detected
        if (hasBottleneck) {
            correlations.add(
                Correlation(
                    finding = "Routing bottleneck detected (${allBottlenecks.size} hop(s) with latency spikes)",
                    rootCause = "Specific network hop causing latency spike",
                    recommendation = "Bottleneck is likely ISP or internet backbone issue - monitor and contact ISP if persistent",
                    type = CorrelationType.CAUSAL,
                    confidence = 0.9,
                    severity = CorrelationSeverity.MEDIUM,
                    category = CorrelationCategory.LATENCY_ROUTING,
                ),
            )
        }

        // Efficient routing
        if (maxHops <= 10 && !hasBottleneck) {
            correlations.add(
                Correlation(
                    finding = "Efficient routing path ($maxHops hops, no bottlenecks)",
                    rootCause = "ISP has good peering and routing efficiency",
                    recommendation = "Routing is optimal - maintain current ISP/configuration",
                    type = CorrelationType.RELATED,
                    confidence = 0.85,
                    severity = CorrelationSeverity.INFORMATIONAL,
                    category = CorrelationCategory.LATENCY_ROUTING,
                ),
            )
        }

        return correlations
    }

    // Security correlation analysis

    private fun analyzeSecurityCorrelations(context: CorrelationContext): List<Correlation> {
        val correlations = mutableListOf<Correlation>()
        val securityScore = context.securityScore ?: return correlations
        val diag = context.activeDiagnostics

        // Poor security + Good performance = Security risk
        if (securityScore < 60.0 && diag.connectivityQuality.toScore() >= 80) {
            val finding =
                "Network performs well (${diag.connectivityQuality}) but has security weaknesses " +
                    "(${securityScore.toInt()}/100)"
            correlations.add(
                Correlation(
                    finding = finding,
                    rootCause = "Weak encryption, outdated protocols, or security misconfigurations",
                    recommendation = "Upgrade to WPA3, enable Protected Management Frames (PMF), disable WPS, update firmware",
                    type = CorrelationType.CONFLICTING,
                    confidence = 0.9,
                    severity = CorrelationSeverity.HIGH,
                    category = CorrelationCategory.SECURITY_CONFIGURATION,
                ),
            )
        }

        // High security + Slightly lower performance = Expected trade-off
        if (securityScore >= 90.0 && diag.connectivityQuality.toScore() in 70..85) {
            correlations.add(
                Correlation(
                    finding = "Strong security (${securityScore.toInt()}/100) with acceptable performance (${diag.connectivityQuality})",
                    rootCause = "Security measures (WPA3, PMF) have minimal performance overhead",
                    recommendation = "Good security posture - performance trade-off is acceptable and expected",
                    type = CorrelationType.RELATED,
                    confidence = 0.75,
                    severity = CorrelationSeverity.INFORMATIONAL,
                    category = CorrelationCategory.SECURITY_CONFIGURATION,
                ),
            )
        }

        // Poor security + Poor performance = Severe risk
        if (securityScore < 50.0 && diag.connectivityQuality.toScore() < 60) {
            correlations.add(
                Correlation(
                    finding = "Poor security (${securityScore.toInt()}/100) AND poor performance (${diag.connectivityQuality})",
                    rootCause = "Network has both security vulnerabilities and connectivity issues",
                    recommendation = "URGENT: Address security first (WPA3, PMF), then optimize performance - network is vulnerable",
                    type = CorrelationType.CAUSAL,
                    confidence = 0.95,
                    severity = CorrelationSeverity.CRITICAL,
                    category = CorrelationCategory.SECURITY_CONFIGURATION,
                ),
            )
        }

        return correlations
    }

    // Topology correlation analysis

    private fun analyzeTopologyCorrelations(context: CorrelationContext): List<Correlation> {
        val correlations = mutableListOf<Correlation>()
        val topologyQuality = context.topologyQuality ?: return correlations
        val rfQuality = context.rfQuality ?: return correlations

        // Poor topology + Good RF = Roaming or multi-AP issues
        if (topologyQuality < 60.0 && rfQuality >= 75.0) {
            correlations.add(
                Correlation(
                    finding = "Poor topology quality (${topologyQuality.toInt()}/100) despite good RF (${rfQuality.toInt()}/100)",
                    rootCause = "Roaming issues, AP placement problems, or mesh network inefficiency",
                    recommendation = "Optimize AP placement, configure roaming thresholds, enable 802.11k/v/r fast roaming",
                    type = CorrelationType.SYMPTOMATIC,
                    confidence = 0.8,
                    severity = CorrelationSeverity.MEDIUM,
                    category = CorrelationCategory.TOPOLOGY_ROAMING,
                ),
            )
        }

        // Good topology + Good RF = Optimal wireless design
        if (topologyQuality >= 80.0 && rfQuality >= 80.0) {
            correlations.add(
                Correlation(
                    finding = "Excellent topology (${topologyQuality.toInt()}/100) and RF quality (${rfQuality.toInt()}/100)",
                    rootCause = "Well-designed wireless network with optimal AP placement and coverage",
                    recommendation = "Wireless infrastructure is optimally configured - maintain current design",
                    type = CorrelationType.RELATED,
                    confidence = 0.9,
                    severity = CorrelationSeverity.INFORMATIONAL,
                    category = CorrelationCategory.TOPOLOGY_ROAMING,
                ),
            )
        }

        return correlations
    }

    // Multi-factor correlation analysis

    private fun analyzeMultiFactorCorrelations(context: CorrelationContext): List<Correlation> {
        val correlations = mutableListOf<Correlation>()
        val diag = context.activeDiagnostics

        // Poor RF + High latency + Low bandwidth = Comprehensive WiFi issue
        val rfQuality = context.rfQuality
        val avgLatencyMs = diag.bestLatency?.inWholeMilliseconds?.toDouble()
        val downloadSpeed = diag.bestDownloadSpeed

        if (rfQuality != null &&
            rfQuality < 60.0 &&
            avgLatencyMs != null &&
            avgLatencyMs > 50.0 &&
            downloadSpeed != null &&
            downloadSpeed < 25.0
        ) {
            val finding =
                "Poor RF (${rfQuality.toInt()}/100), high latency (${avgLatencyMs.toInt()}ms), " +
                    "and low bandwidth ($downloadSpeed Mbps)"
            val recommendation =
                "PRIORITY: Fix RF immediately - move closer to AP, reduce obstacles, add access points, " +
                    "optimize channel"
            correlations.add(
                Correlation(
                    finding = finding,
                    rootCause = "Comprehensive WiFi connectivity breakdown - RF is root cause affecting all metrics",
                    recommendation = recommendation,
                    type = CorrelationType.CAUSAL,
                    confidence = 0.95,
                    severity = CorrelationSeverity.CRITICAL,
                    category = CorrelationCategory.RF_CONNECTIVITY,
                ),
            )
        }

        // Slow DNS + Routing bottleneck = ISP issue
        if (diag.dnsTests.isNotEmpty() && diag.tracerouteResults.isNotEmpty()) {
            val avgDnsTime = diag.dnsTests.map { it.resolutionTime.inWholeMilliseconds }.average()
            val allBottlenecks = diag.tracerouteResults.flatMap { it.findBottlenecks() }
            val hasBottleneck = allBottlenecks.isNotEmpty()

            if (avgDnsTime > 100.0 && hasBottleneck) {
                correlations.add(
                    Correlation(
                        finding = "Slow DNS (${avgDnsTime.toInt()}ms) combined with routing bottlenecks",
                        rootCause = "ISP infrastructure issues affecting both DNS and routing",
                        recommendation = "Contact ISP - multiple symptoms suggest ISP-side problems. Consider alternative ISP.",
                        type = CorrelationType.SYMPTOMATIC,
                        confidence = 0.85,
                        severity = CorrelationSeverity.HIGH,
                        category = CorrelationCategory.LATENCY_ROUTING,
                    ),
                )
            }
        }

        // Good everything = Optimal network
        if (rfQuality != null &&
            rfQuality >= 85.0 &&
            avgLatencyMs != null &&
            avgLatencyMs < 20.0 &&
            downloadSpeed != null &&
            downloadSpeed >= 100.0 &&
            context.securityScore != null &&
            context.securityScore!! >= 80.0
        ) {
            val finding =
                "Excellent across all metrics: RF (${rfQuality.toInt()}), " +
                    "latency (${avgLatencyMs.toInt()}ms), bandwidth ($downloadSpeed Mbps), " +
                    "security (${context.securityScore!!.toInt()})"
            correlations.add(
                Correlation(
                    finding = finding,
                    rootCause = "Comprehensive network optimization - RF, ISP, routing, and security all optimal",
                    recommendation = "Network is performing at peak - document configuration for future reference",
                    type = CorrelationType.RELATED,
                    confidence = 0.95,
                    severity = CorrelationSeverity.INFORMATIONAL,
                    category = CorrelationCategory.RF_CONNECTIVITY,
                ),
            )
        }

        return correlations
    }

    // Anomaly detection

    private fun detectAnomalies(context: CorrelationContext): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()
        val diag = context.activeDiagnostics

        // Ping anomalies: packet loss without latency issues
        val avgLatencyMs = diag.bestLatency?.inWholeMilliseconds?.toDouble()
        if (diag.worstPacketLoss >= 10.0 && avgLatencyMs != null && avgLatencyMs < 30.0) {
            anomalies.add(
                Anomaly(
                    metric = "Packet Loss",
                    expectedRange = "0-2% (given low latency)",
                    actualValue = "${diag.worstPacketLoss}%",
                    description = "High packet loss without corresponding latency increase - unusual pattern",
                    possibleCauses =
                        listOf(
                            "Intermittent RF interference",
                            "Hardware failure (NIC, AP)",
                            "Firmware bug",
                            "Channel congestion bursts",
                        ),
                    severity = AnomalySeverity.HIGH,
                ),
            )
        }

        // Bandwidth anomaly: very low upload despite good download
        val downloadSpeed = diag.bestDownloadSpeed
        val uploadSpeed = diag.bestUploadSpeed
        if (downloadSpeed != null &&
            uploadSpeed != null &&
            downloadSpeed > 50.0 &&
            uploadSpeed < 1.0
        ) {
            anomalies.add(
                Anomaly(
                    metric = "Upload Bandwidth",
                    expectedRange = "At least 10% of download (${downloadSpeed * 0.1} Mbps)",
                    actualValue = "$uploadSpeed Mbps",
                    description = "Extremely low upload compared to download - beyond normal asymmetry",
                    possibleCauses =
                        listOf(
                            "ISP upload throttling or QoS",
                            "Upload channel interference on WiFi",
                            "Asymmetric routing issue",
                            "Firewall or IDS blocking upstream",
                        ),
                    severity = AnomalySeverity.MEDIUM,
                ),
            )
        }

        // Jitter anomaly: high jitter despite low average latency
        val jitterMs =
            diag.pingTests
                .filter {
                    it.isSuccessful
                }.mapNotNull { it.jitter?.inWholeMilliseconds?.toDouble() }
                .minOrNull()
        if (avgLatencyMs != null &&
            jitterMs != null &&
            avgLatencyMs < 30.0 &&
            jitterMs > 20.0
        ) {
            anomalies.add(
                Anomaly(
                    metric = "Jitter",
                    expectedRange = "< 10ms (given ${avgLatencyMs.toInt()}ms avg latency)",
                    actualValue = "${jitterMs.toInt()}ms",
                    description = "High latency variation despite low average - suggests instability",
                    possibleCauses =
                        listOf(
                            "Bursty network traffic",
                            "CPU throttling or power saving",
                            "Competing traffic without QoS",
                            "Variable routing paths",
                        ),
                    severity = AnomalySeverity.MEDIUM,
                ),
            )
        }

        // RF anomaly: connectivity poor despite supposedly good RF
        val rfQuality = context.rfQuality
        if (rfQuality != null &&
            rfQuality >= 75.0 &&
            diag.connectivityQuality.toScore() < 60
        ) {
            anomalies.add(
                Anomaly(
                    metric = "Connectivity Quality",
                    expectedRange = "GOOD or better (given RF ${rfQuality.toInt()}/100)",
                    actualValue = diag.connectivityQuality.name,
                    description = "Poor connectivity despite good RF - contradictory signals",
                    possibleCauses =
                        listOf(
                            "RF measurement inaccurate or stale",
                            "Hidden node problem (interference not detected)",
                            "AP overload despite good signal",
                            "Authentication or association issues",
                        ),
                    severity = AnomalySeverity.HIGH,
                ),
            )
        }

        return anomalies
    }

    // Helper: Calculate correlation confidence
    private fun calculateConfidence(
        metric1: Double,
        metric2: Double,
        inverse: Boolean = false,
    ): Double {
        // Normalize metrics to 0-100 scale
        val norm1 = metric1.coerceIn(0.0, 100.0) / 100.0
        val norm2 = metric2.coerceIn(0.0, 100.0) / 100.0

        // Calculate correlation strength
        val diff =
            if (inverse) {
                abs(norm1 - (1.0 - norm2))
            } else {
                abs(norm1 - norm2)
            }

        // Confidence is inverse of difference
        return (1.0 - diff).coerceIn(0.0, 1.0)
    }
}

/**
 * Context for correlation analysis.
 */
private data class CorrelationContext(
    val activeDiagnostics: ActiveDiagnostics,
    val rfQuality: Double?,
    val topologyQuality: Double?,
    val securityScore: Double?,
    val healthScore: NetworkHealthScore,
)

/**
 * Result of integrating passive and active analysis.
 *
 * @property healthScore Overall network health score
 * @property activeDiagnostics Active diagnostic results
 * @property recommendations Actionable recommendations
 * @property correlations Identified correlations between metrics
 * @property anomalies Detected anomalies requiring investigation
 * @property location Optional location/site identifier
 * @property timestamp When analysis was performed
 * @property rfQualityScore RF quality score (if available)
 * @property topologyQualityScore Topology quality score (if available)
 * @property securityScore Security score (if available)
 */
data class IntegratedAnalysis(
    val healthScore: NetworkHealthScore,
    val activeDiagnostics: ActiveDiagnostics,
    val recommendations: RecommendationSet,
    val correlations: List<Correlation>,
    val anomalies: List<Anomaly> = emptyList(),
    val location: String?,
    val timestamp: Long,
    val rfQualityScore: Double?,
    val topologyQualityScore: Double?,
    val securityScore: Double?,
) {
    /**
     * Whether this is a healthy network.
     */
    val isHealthy: Boolean = healthScore.isHealthy

    /**
     * Whether immediate action is required.
     */
    val requiresImmediateAction: Boolean =
        recommendations.hasCriticalIssues ||
            correlations.any { it.severity == CorrelationSeverity.CRITICAL } ||
            anomalies.any { it.severity == AnomalySeverity.CRITICAL }

    /**
     * High-confidence correlations (>= 0.8).
     */
    val highConfidenceCorrelations: List<Correlation> =
        correlations.filter { it.confidence >= 0.8 }

    /**
     * Critical correlations requiring immediate attention.
     */
    val criticalCorrelations: List<Correlation> =
        correlations.filter { it.severity == CorrelationSeverity.CRITICAL }

    /**
     * Executive summary of the analysis.
     */
    fun executiveSummary(): String =
        buildString {
            appendLine("=== Network Analysis Summary ===")
            location?.let { appendLine("Location: $it") }
            appendLine("Overall Health: ${healthScore.overallHealth} (${healthScore.overallScore.toInt()}/100)")
            appendLine()

            if (highConfidenceCorrelations.isNotEmpty()) {
                appendLine("Key Findings (${highConfidenceCorrelations.size} high-confidence):")
                highConfidenceCorrelations.take(5).forEach { appendLine("  • ${it.finding}") }
                if (highConfidenceCorrelations.size > 5) {
                    appendLine("  ... and ${highConfidenceCorrelations.size - 5} more")
                }
                appendLine()
            }

            if (anomalies.isNotEmpty()) {
                appendLine("⚠️  ${anomalies.size} anomaly(ies) detected requiring investigation")
                appendLine()
            }

            if (recommendations.hasCriticalIssues) {
                appendLine("🚨 ${recommendations.critical.size} CRITICAL issue(s) require immediate attention")
            }

            if (recommendations.quickWins.isNotEmpty()) {
                appendLine("💡 ${recommendations.quickWins.size} quick win(s) available")
            }
        }
}

/**
 * Correlation between passive and active metrics.
 *
 * @property finding What was observed in the correlation
 * @property rootCause Likely root cause explanation
 * @property recommendation Actionable recommendation
 * @property type Type of correlation relationship
 * @property confidence Confidence level (0.0-1.0)
 * @property severity Impact severity
 * @property category Correlation category for grouping
 */
data class Correlation(
    val finding: String,
    val rootCause: String,
    val recommendation: String,
    val type: CorrelationType = CorrelationType.RELATED,
    val confidence: Double = 0.7,
    val severity: CorrelationSeverity = CorrelationSeverity.MEDIUM,
    val category: CorrelationCategory = CorrelationCategory.GENERAL,
) {
    init {
        require(confidence in 0.0..1.0) { "Confidence must be 0.0-1.0, got $confidence" }
    }
}

/**
 * Detected anomaly in network metrics.
 *
 * @property metric The metric exhibiting anomalous behavior
 * @property expectedRange Expected value or range
 * @property actualValue Actual observed value
 * @property description Human-readable description
 * @property possibleCauses List of potential root causes
 * @property severity Anomaly severity
 */
data class Anomaly(
    val metric: String,
    val expectedRange: String,
    val actualValue: String,
    val description: String,
    val possibleCauses: List<String>,
    val severity: AnomalySeverity,
)

/**
 * Type of correlation relationship.
 */
enum class CorrelationType {
    /** Direct cause-effect relationship */
    CAUSAL,

    /** Symptoms of same underlying issue */
    SYMPTOMATIC,

    /** Associated but not directly causal */
    RELATED,

    /** Contradictory signals requiring investigation */
    CONFLICTING,
}

/**
 * Correlation severity levels.
 */
enum class CorrelationSeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFORMATIONAL,
}

/**
 * Anomaly severity levels.
 */
enum class AnomalySeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
}

/**
 * Correlation category for organization.
 */
enum class CorrelationCategory {
    RF_CONNECTIVITY,
    RF_INTERFERENCE,
    LATENCY_ROUTING,
    BANDWIDTH_RF,
    BANDWIDTH_ISP,
    DNS_SERVER,
    SECURITY_CONFIGURATION,
    TOPOLOGY_ROAMING,
    GENERAL,
}

/**
 * Helper extensions
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
