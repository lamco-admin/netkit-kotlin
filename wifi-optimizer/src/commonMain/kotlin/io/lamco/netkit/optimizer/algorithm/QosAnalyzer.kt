package io.lamco.netkit.optimizer.algorithm

import io.lamco.netkit.optimizer.model.QosGoals
import io.lamco.netkit.optimizer.model.WmmAccessCategory
import kotlin.math.abs

/**
 * QoS and WMM (WiFi Multimedia) analyzer
 *
 * Analyzes Quality of Service configuration and airtime distribution to identify
 * performance issues and recommend improvements.
 *
 * Features:
 * - WMM (IEEE 802.11e) effectiveness analysis
 * - Airtime fairness assessment using Gini coefficient
 * - Client prioritization recommendations
 * - Traffic class optimization
 *
 * Based on IEEE 802.11e QoS enhancements and airtime fairness research.
 */
class QosAnalyzer {

    /**
     * Analyze WMM (WiFi Multimedia) effectiveness
     *
     * Evaluates WMM configuration against traffic profile to determine if
     * QoS is configured optimally for the network's usage patterns.
     *
     * @param wmmConfig Current WMM configuration (null if disabled)
     * @param trafficProfile Observed or expected traffic distribution
     * @return WMM analysis with issues and recommendations
     */
    fun analyzeWmm(
        wmmConfig: WmmConfiguration?,
        trafficProfile: TrafficProfile
    ): WmmAnalysisResult {
        val issues = mutableListOf<WmmIssue>()
        val recommendations = mutableListOf<String>()

        
        if (wmmConfig == null) {
            issues.add(WmmIssue.WmmDisabled)
            recommendations.add("Enable WMM (802.11e) for QoS support")
            return WmmAnalysisResult(
                isEffective = false,
                issues = issues,
                recommendations = recommendations,
                expectedImprovement = "WMM can improve voice/video quality by 30-50%"
            )
        }

        
        if (trafficProfile.voicePercent > 10.0 && !wmmConfig.voiceEnabled) {
            issues.add(WmmIssue.InsufficientPrioritization("Voice"))
            recommendations.add("Enable voice (AC_VO) queue for VoIP traffic (${trafficProfile.voicePercent.toInt()}% of traffic)")
        }

        
        if (trafficProfile.videoPercent > 20.0 && !wmmConfig.videoEnabled) {
            issues.add(WmmIssue.InsufficientPrioritization("Video"))
            recommendations.add("Enable video (AC_VI) queue for video streaming (${trafficProfile.videoPercent.toInt()}% of traffic)")
        }

        
        wmmConfig.queueParameters.forEach { (queue, params) ->
            if (!isQueueConfiguredCorrectly(queue, params)) {
                issues.add(WmmIssue.QueueMisconfigured(queue, "Parameters not optimal for $queue traffic"))
                recommendations.add("Adjust $queue queue parameters (CWmin/CWmax/AIFSN)")
            }
        }

        val isEffective = issues.isEmpty() || issues.all { it is WmmIssue.QueueMisconfigured }

        val improvement = if (!isEffective) {
            when {
                trafficProfile.voicePercent > 20 -> "Voice quality improvement: 40-60%, latency reduction: 50%"
                trafficProfile.videoPercent > 30 -> "Video quality improvement: 30-40%, jitter reduction: 40%"
                else -> "Overall QoS improvement: 20-30%"
            }
        } else {
            "WMM is configured effectively"
        }

        return WmmAnalysisResult(
            isEffective = isEffective,
            issues = issues,
            recommendations = recommendations,
            expectedImprovement = improvement
        )
    }

    /**
     * Assess airtime fairness across clients
     *
     * Calculates airtime distribution and detects unfair usage patterns
     * that can degrade network performance for all users.
     *
     * Uses Gini coefficient to measure fairness:
     * - 0.0 = Perfect equality
     * - 1.0 = Perfect inequality
     *
     * @param clients List of client airtime metrics
     * @return Airtime fairness analysis with hog detection
     */
    fun assessAirtimeFairness(
        clients: List<ClientAirtimeMetrics>
    ): AirtimeFairnessResult {
        require(clients.isNotEmpty()) {
            "Cannot assess fairness with no clients"
        }

        
        val totalAirtime = clients.sumOf { it.airtimeMs }

        
        val percentages = clients.map { (it.airtimeMs.toDouble() / totalAirtime) * 100.0 }

        
        val gini = calculateGiniCoefficient(percentages)

        val fairnessScore = ((1.0 - gini) * 100).coerceIn(0.0, 100.0)

        val hogs = detectAirtimeHogs(clients, totalAirtime)

        return AirtimeFairnessResult(
            giniCoefficient = gini,
            fairnessScore = fairnessScore,
            airtimeHogs = hogs,
            isFair = gini < 0.4,
            recommendations = buildFairnessRecommendations(gini, hogs)
        )
    }

    /**
     * Recommend client prioritization rules
     *
     * Analyzes client characteristics and network goals to recommend
     * which clients should receive priority treatment.
     *
     * @param clients List of client metrics
     * @param goals QoS goals and priorities
     * @return Prioritization recommendations
     */
    fun recommendPrioritization(
        clients: List<ClientMetrics>,
        goals: QosGoals
    ): PrioritizationRecommendations {
        val highPriority = mutableListOf<String>()
        val mediumPriority = mutableListOf<String>()
        val lowPriority = mutableListOf<String>()

        for (client in clients) {
            val priority = determinePriority(client, goals)

            when (priority) {
                ClientPriority.HIGH -> highPriority.add(client.macAddress)
                ClientPriority.MEDIUM -> mediumPriority.add(client.macAddress)
                ClientPriority.LOW -> lowPriority.add(client.macAddress)
            }
        }

        return PrioritizationRecommendations(
            highPriority = highPriority,
            mediumPriority = mediumPriority,
            lowPriority = lowPriority,
            rationale = buildPrioritizationRationale(goals)
        )
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    /**
     * Calculate Gini coefficient for inequality measurement
     *
     * Gini = (Σ|x_i - x_j|) / (2n² * mean(x))
     */
    private fun calculateGiniCoefficient(values: List<Double>): Double {
        val n = values.size
        if (n < 2) return 0.0

        val mean = values.average()
        if (mean == 0.0) return 0.0

        var sum = 0.0
        for (i in values.indices) {
            for (j in values.indices) {
                sum += abs(values[i] - values[j])
            }
        }

        return sum / (2.0 * n * n * mean)
    }

    /**
     * Detect clients consuming excessive airtime
     */
    private fun detectAirtimeHogs(
        clients: List<ClientAirtimeMetrics>,
        totalAirtime: Long
    ): List<AirtimeHog> {
        val fairShare = 100.0 / clients.size
        val hogThreshold = (fairShare * 2.0).coerceAtLeast(30.0)  // At least 2x fair share or 30%

        return clients.mapNotNull { client ->
            val percent = (client.airtimeMs.toDouble() / totalAirtime) * 100.0

            if (percent > hogThreshold) {
                val reason = determineHogReason(client)
                AirtimeHog(
                    macAddress = client.macAddress,
                    airtimePercent = percent,
                    expectedPercent = fairShare,
                    excessPercent = percent - fairShare,
                    reason = reason
                )
            } else null
        }.sortedByDescending { it.airtimePercent }
    }

    private fun determineHogReason(client: ClientAirtimeMetrics): AirtimeHogReason {
        return when {
            client.isLegacyDevice -> AirtimeHogReason.LEGACY_DEVICE
            client.signalStrength < -75 -> AirtimeHogReason.INEFFICIENT_CLIENT
            client.dataRate < 50.0 -> AirtimeHogReason.MISCONFIGURED
            else -> AirtimeHogReason.HIGH_TRAFFIC
        }
    }

    private fun isQueueConfiguredCorrectly(queue: String, params: QueueParams): Boolean {
        // Validate against IEEE 802.11e recommended values
        return when (queue) {
            "AC_VO" -> params.aifsn == 2 && params.cwMin in 3..7
            "AC_VI" -> params.aifsn == 2 && params.cwMin in 7..15
            "AC_BE" -> params.aifsn == 3 && params.cwMin in 15..1023
            "AC_BK" -> params.aifsn == 7 && params.cwMin in 15..1023
            else -> true
        }
    }

    private fun determinePriority(client: ClientMetrics, goals: QosGoals): ClientPriority {
        return when {
            client.hasVoipTraffic && goals.voicePriority >= 3 -> ClientPriority.HIGH
            client.hasVideoTraffic && goals.videoPriority >= 2 -> ClientPriority.HIGH
            client.isLegacyDevice -> ClientPriority.LOW
            client.signalStrength < -75 -> ClientPriority.LOW
            else -> ClientPriority.MEDIUM
        }
    }

    private fun buildFairnessRecommendations(gini: Double, hogs: List<AirtimeHog>): List<String> {
        val recommendations = mutableListOf<String>()

        when {
            gini > 0.6 -> recommendations.add("Enable airtime fairness to prevent client monopolization")
            gini > 0.4 -> recommendations.add("Consider airtime fairness for better performance distribution")
        }

        hogs.forEach { hog ->
            when (hog.reason) {
                AirtimeHogReason.LEGACY_DEVICE -> recommendations.add("Upgrade or isolate legacy 802.11b/g device ${hog.macAddress}")
                AirtimeHogReason.INEFFICIENT_CLIENT -> recommendations.add("Improve signal for client ${hog.macAddress} (move closer or add AP)")
                AirtimeHogReason.MISCONFIGURED -> recommendations.add("Check band steering for client ${hog.macAddress}")
                AirtimeHogReason.HIGH_TRAFFIC -> recommendations.add("Client ${hog.macAddress} has legitimate high usage")
            }
        }

        return recommendations
    }

    private fun buildPrioritizationRationale(goals: QosGoals): String {
        return when {
            goals.voicePriority >= 3 -> "Prioritizing voice traffic for VoIP quality"
            goals.videoPriority >= 3 -> "Prioritizing video traffic for streaming quality"
            else -> "Balanced prioritization for general traffic"
        }
    }
}

// ========================================
// Data Classes
// ========================================

data class WmmConfiguration(
    val voiceEnabled: Boolean,
    val videoEnabled: Boolean,
    val bestEffortEnabled: Boolean,
    val backgroundEnabled: Boolean,
    val queueParameters: Map<String, QueueParams>
)

data class QueueParams(
    val cwMin: Int,
    val cwMax: Int,
    val aifsn: Int,
    val txopLimit: Int
)

data class TrafficProfile(
    val voicePercent: Double,
    val videoPercent: Double,
    val dataPercent: Double,
    val backgroundPercent: Double
)

data class WmmAnalysisResult(
    val isEffective: Boolean,
    val issues: List<WmmIssue>,
    val recommendations: List<String>,
    val expectedImprovement: String
)

sealed class WmmIssue {
    data class QueueMisconfigured(val queue: String, val problem: String) : WmmIssue()
    data class InsufficientPrioritization(val trafficType: String) : WmmIssue()
    object WmmDisabled : WmmIssue()
    object ClientsNotWmmCapable : WmmIssue()
}

data class ClientAirtimeMetrics(
    val macAddress: String,
    val airtimeMs: Long,
    val isLegacyDevice: Boolean,
    val signalStrength: Int,
    val dataRate: Double
)

data class AirtimeFairnessResult(
    val giniCoefficient: Double,
    val fairnessScore: Double,
    val airtimeHogs: List<AirtimeHog>,
    val isFair: Boolean,
    val recommendations: List<String>
)

data class AirtimeHog(
    val macAddress: String,
    val airtimePercent: Double,
    val expectedPercent: Double,
    val excessPercent: Double,
    val reason: AirtimeHogReason
)

enum class AirtimeHogReason {
    LEGACY_DEVICE,
    HIGH_TRAFFIC,
    INEFFICIENT_CLIENT,
    MISCONFIGURED
}

data class ClientMetrics(
    val macAddress: String,
    val signalStrength: Int,
    val hasVoipTraffic: Boolean,
    val hasVideoTraffic: Boolean,
    val isLegacyDevice: Boolean
)

data class PrioritizationRecommendations(
    val highPriority: List<String>,
    val mediumPriority: List<String>,
    val lowPriority: List<String>,
    val rationale: String
)

enum class ClientPriority {
    HIGH, MEDIUM, LOW
}
