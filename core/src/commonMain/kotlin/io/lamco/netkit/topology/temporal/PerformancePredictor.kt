package io.lamco.netkit.topology.temporal

import io.lamco.netkit.model.topology.*
import kotlin.math.max
import kotlin.math.min

/**
 * Performance prediction engine for forecasting WiFi network behavior
 *
 * Uses historical data and trend analysis to predict:
 * - Future signal strength levels
 * - Expected network health scores
 * - Potential coverage gaps
 * - Roaming quality expectations
 * - Optimal connection times
 *
 * Predictions include confidence intervals and are based on linear
 * extrapolation with variance-based uncertainty estimation.
 *
 * @property minHistoricalDataPoints Minimum data points for reliable prediction
 * @property maxPredictionHorizonMillis Maximum time to predict ahead (default: 24 hours)
 * @property confidenceInterval Confidence interval for predictions (default: 95%)
 */
class PerformancePredictor(
    val minHistoricalDataPoints: Int = 20,
    val maxPredictionHorizonMillis: Long = 86400_000, // 24 hours
    val confidenceInterval: Double = 0.95,
) {
    init {
        require(minHistoricalDataPoints >= 3) {
            "Minimum historical data points must be >= 3, got $minHistoricalDataPoints"
        }
        require(maxPredictionHorizonMillis > 0) {
            "Max prediction horizon must be positive, got $maxPredictionHorizonMillis"
        }
        require(confidenceInterval in 0.0..1.0) {
            "Confidence interval must be in [0, 1], got $confidenceInterval"
        }
    }

    /**
     * Predict future signal strength for an AP
     *
     * Uses linear regression on historical observations to forecast
     * signal strength at a specific future time.
     */
    fun predictApSignalStrength(
        history: ApHistory,
        horizonMillis: Long,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): SignalPrediction {
        require(horizonMillis > 0) {
            "Horizon must be positive, got $horizonMillis"
        }
        require(horizonMillis <= maxPredictionHorizonMillis) {
            "Horizon $horizonMillis exceeds maximum $maxPredictionHorizonMillis"
        }

        val targetTime = currentTimeMillis + horizonMillis
        val recentObs = history.recentObservations(3600_000, currentTimeMillis) // Last hour

        if (recentObs.size < minHistoricalDataPoints) {
            return SignalPrediction(
                bssid = history.bssid,
                predictionTimeMillis = targetTime,
                predictedRssiDbm = history.averageRssiDbm.toInt(),
                lowerBoundRssiDbm = history.minRssiDbm,
                upperBoundRssiDbm = history.peakRssiDbm,
                confidence = PredictionConfidence.LOW,
                baselineRssiDbm = history.averageRssiDbm.toInt(),
            )
        }

        // Calculate trend using linear regression
        val (slope, intercept) = calculateLinearRegression(recentObs)

        // Predict at target time
        val timeSinceFirst = targetTime - recentObs.first().timestampMillis
        val predicted = (intercept + slope * timeSinceFirst).toInt().coerceIn(-120, 0)

        // Calculate confidence bounds based on std dev
        val stdDev = history.rssiStandardDeviation
        val marginOfError = (stdDev * 1.96).toInt() // 95% confidence interval

        val lowerBound = (predicted - marginOfError).coerceIn(-120, 0)
        val upperBound = (predicted + marginOfError).coerceIn(-120, 0)

        val confidence =
            when {
                recentObs.size >= 100 && stdDev < 3.0 -> PredictionConfidence.HIGH
                recentObs.size >= 50 && stdDev < 7.0 -> PredictionConfidence.MEDIUM
                else -> PredictionConfidence.LOW
            }

        return SignalPrediction(
            bssid = history.bssid,
            predictionTimeMillis = targetTime,
            predictedRssiDbm = predicted,
            lowerBoundRssiDbm = lowerBound,
            upperBoundRssiDbm = upperBound,
            confidence = confidence,
            baselineRssiDbm = history.averageRssiDbm.toInt(),
        )
    }

    /**
     * Predict network health at future time
     *
     * Forecasts overall network health score based on current trends.
     */
    fun predictNetworkHealth(
        networkTrend: NetworkTrend,
        horizonMillis: Long,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): NetworkHealthPrediction {
        require(horizonMillis > 0) {
            "Horizon must be positive, got $horizonMillis"
        }
        require(horizonMillis <= maxPredictionHorizonMillis) {
            "Horizon $horizonMillis exceeds maximum $maxPredictionHorizonMillis"
        }

        val currentHealth = networkTrend.healthScore
        val recentTrend = networkTrend.recentNetworkTrend()

        // Estimate health change based on trend
        val healthChange =
            when (recentTrend) {
                NetworkTrendDirection.STRONGLY_IMPROVING -> (horizonMillis / 3600_000.0) * 5.0
                NetworkTrendDirection.IMPROVING -> (horizonMillis / 3600_000.0) * 2.0
                NetworkTrendDirection.STABLE -> 0.0
                NetworkTrendDirection.DEGRADING -> (horizonMillis / 3600_000.0) * -2.0
                NetworkTrendDirection.STRONGLY_DEGRADING -> (horizonMillis / 3600_000.0) * -5.0
                NetworkTrendDirection.INSUFFICIENT_DATA -> 0.0
            }

        val predictedHealth = (currentHealth + healthChange.toInt()).coerceIn(0, 100)

        // Estimate confidence based on data quality
        val confidence =
            when {
                networkTrend.totalObservationCount >= 500 -> PredictionConfidence.HIGH
                networkTrend.totalObservationCount >= 100 -> PredictionConfidence.MEDIUM
                else -> PredictionConfidence.LOW
            }

        val predictedCategory = NetworkHealth.fromScore(predictedHealth)

        return NetworkHealthPrediction(
            ssid = networkTrend.ssid,
            predictionTimeMillis = currentTimeMillis + horizonMillis,
            currentHealthScore = currentHealth,
            predictedHealthScore = predictedHealth,
            currentHealth = networkTrend.health,
            predictedHealth = predictedCategory,
            confidence = confidence,
            trend = recentTrend,
        )
    }

    /**
     * Predict coverage quality at future time
     *
     * Estimates whether coverage will improve or degrade.
     */
    fun predictCoverageQuality(
        networkTrend: NetworkTrend,
        horizonMillis: Long,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): CoveragePrediction {
        require(horizonMillis > 0) {
            "Horizon must be positive, got $horizonMillis"
        }

        val visibleAps = networkTrend.currentlyVisibleAps(currentTimeMillis = currentTimeMillis)
        val currentApCount = visibleAps.size

        // Predict AP availability based on recent churn
        val recentDisappeared =
            networkTrend
                .recentlyDisappearedAps(
                    windowMillis = 3600_000,
                    currentTimeMillis = currentTimeMillis,
                ).size

        val disappearRate = recentDisappeared / 3600_000.0 // per millisecond
        val expectedDisappearances = (disappearRate * horizonMillis).toInt()
        val predictedApCount = max(1, currentApCount - expectedDisappearances)

        // Predict average signal
        val signalPredictions =
            visibleAps.map { history ->
                predictApSignalStrength(history, horizonMillis, currentTimeMillis)
            }

        val predictedAvgSignal =
            signalPredictions
                .map { it.predictedRssiDbm }
                .average()
                .takeIf { it.isFinite() } ?: -70.0

        val coverageQuality =
            when {
                predictedAvgSignal >= -60 && predictedApCount >= currentApCount ->
                    CoverageQuality.EXCELLENT
                predictedAvgSignal >= -70 && predictedApCount >= currentApCount * 0.8 ->
                    CoverageQuality.GOOD
                predictedAvgSignal >= -80 ->
                    CoverageQuality.FAIR
                else ->
                    CoverageQuality.POOR
            }

        val confidence =
            if (signalPredictions.all { it.confidence.isAcceptable }) {
                PredictionConfidence.MEDIUM
            } else {
                PredictionConfidence.LOW
            }

        return CoveragePrediction(
            predictionTimeMillis = currentTimeMillis + horizonMillis,
            currentApCount = currentApCount,
            predictedApCount = predictedApCount,
            currentAvgSignalDbm = networkTrend.averageNetworkRssiDbm.toInt(),
            predictedAvgSignalDbm = predictedAvgSignal.toInt(),
            quality = coverageQuality,
            confidence = confidence,
        )
    }

    /**
     * Recommend optimal time to connect/roam
     *
     * Analyzes historical patterns to suggest best connection windows.
     */
    fun recommendOptimalConnectionTime(
        history: ApHistory,
        lookAheadHours: Int = 4,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): ConnectionTimeRecommendation {
        require(lookAheadHours > 0) {
            "Look ahead hours must be positive, got $lookAheadHours"
        }

        val predictions =
            (1..lookAheadHours).map { hour ->
                val horizonMillis = hour * 3600_000L
                val prediction = predictApSignalStrength(history, horizonMillis, currentTimeMillis)
                hour to prediction
            }

        // Find hour with best predicted signal
        val bestPrediction = predictions.maxByOrNull { it.second.predictedRssiDbm }
        val worstPrediction = predictions.minByOrNull { it.second.predictedRssiDbm }

        val currentSignal =
            history.recentAverageRssi(60_000, currentTimeMillis)?.toInt()
                ?: history.averageRssiDbm.toInt()

        val recommendation =
            when {
                bestPrediction == null || worstPrediction == null ->
                    ConnectionTiming.IMMEDIATE

                bestPrediction.second.predictedRssiDbm - currentSignal >= 10 ->
                    ConnectionTiming.WAIT_FOR_IMPROVEMENT

                currentSignal >= -60 ->
                    ConnectionTiming.IMMEDIATE

                worstPrediction.second.predictedRssiDbm - currentSignal <= -10 ->
                    ConnectionTiming.IMMEDIATE

                else ->
                    ConnectionTiming.FLEXIBLE
            }

        return ConnectionTimeRecommendation(
            bssid = history.bssid,
            currentSignalDbm = currentSignal,
            bestTimeHoursAhead = bestPrediction?.first ?: 0,
            bestPredictedSignalDbm = bestPrediction?.second?.predictedRssiDbm ?: currentSignal,
            worstTimeHoursAhead = worstPrediction?.first ?: 0,
            worstPredictedSignalDbm = worstPrediction?.second?.predictedRssiDbm ?: currentSignal,
            recommendation = recommendation,
        )
    }

    /**
     * Predict likelihood of connection issues
     *
     * Estimates probability of disconnections or poor performance.
     */
    fun predictConnectionIssues(
        history: ApHistory,
        horizonMillis: Long,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): IssuesPrediction {
        val signalPrediction = predictApSignalStrength(history, horizonMillis, currentTimeMillis)

        // Calculate issue probability based on multiple factors
        var issueProbability = 0.0

        // Factor 1: Predicted signal strength
        when {
            signalPrediction.predictedRssiDbm < -85 -> issueProbability += 0.4
            signalPrediction.predictedRssiDbm < -75 -> issueProbability += 0.2
            signalPrediction.predictedRssiDbm < -65 -> issueProbability += 0.1
        }

        // Factor 2: Signal stability
        when (history.signalStability) {
            SignalStability.VERY_UNSTABLE -> issueProbability += 0.3
            SignalStability.UNSTABLE -> issueProbability += 0.2
            SignalStability.MODERATE -> issueProbability += 0.1
            else -> {} // No additional risk
        }

        // Factor 3: Historical disconnection rate
        if (history.connectionCount > 0) {
            val disconnectRate = history.disconnectionCount.toDouble() / history.connectionCount
            issueProbability += disconnectRate * 0.3
        }

        // Cap at 1.0
        issueProbability = min(issueProbability, 1.0)

        val riskLevel =
            when {
                issueProbability >= 0.7 -> RiskLevel.HIGH
                issueProbability >= 0.4 -> RiskLevel.MEDIUM
                issueProbability >= 0.2 -> RiskLevel.LOW
                else -> RiskLevel.MINIMAL
            }

        val likelyIssues = mutableListOf<String>()
        if (signalPrediction.predictedRssiDbm < -80) {
            likelyIssues.add("Weak signal (${signalPrediction.predictedRssiDbm}dBm)")
        }
        if (history.signalStability !in listOf(SignalStability.VERY_STABLE, SignalStability.STABLE)) {
            likelyIssues.add("Unstable connection")
        }
        if (history.disconnectionCount >= 3) {
            likelyIssues.add("Frequent disconnections")
        }

        return IssuesPrediction(
            bssid = history.bssid,
            predictionTimeMillis = currentTimeMillis + horizonMillis,
            issueProbability = issueProbability,
            riskLevel = riskLevel,
            likelyIssues = likelyIssues,
            signalPrediction = signalPrediction,
        )
    }

    /**
     * Calculate linear regression (slope, intercept)
     */
    private fun calculateLinearRegression(observations: List<SignalObservation>): Pair<Double, Double> {
        if (observations.size < 2) return Pair(0.0, observations.firstOrNull()?.rssiDbm?.toDouble() ?: -70.0)

        val firstTime = observations.first().timestampMillis.toDouble()
        val x = observations.map { (it.timestampMillis - firstTime).toDouble() }
        val y = observations.map { it.rssiDbm.toDouble() }

        val n = observations.size
        val xMean = x.average()
        val yMean = y.average()

        val numerator = x.zip(y).sumOf { (xi, yi) -> (xi - xMean) * (yi - yMean) }
        val denominator = x.sumOf { xi -> (xi - xMean) * (xi - xMean) }

        val slope = if (denominator != 0.0) numerator / denominator else 0.0
        val intercept = yMean - slope * xMean

        return Pair(slope, intercept)
    }
}

/**
 * Signal strength prediction
 *
 * @property bssid Access point MAC address
 * @property predictionTimeMillis When this prediction is for
 * @property predictedRssiDbm Predicted RSSI value
 * @property lowerBoundRssiDbm Lower confidence bound
 * @property upperBoundRssiDbm Upper confidence bound
 * @property confidence Prediction confidence level
 * @property baselineRssiDbm Current/baseline RSSI for comparison
 */
data class SignalPrediction(
    val bssid: String,
    val predictionTimeMillis: Long,
    val predictedRssiDbm: Int,
    val lowerBoundRssiDbm: Int,
    val upperBoundRssiDbm: Int,
    val confidence: PredictionConfidence,
    val baselineRssiDbm: Int,
) {
    /**
     * Expected signal change from baseline
     */
    val expectedChangeDbm: Int
        get() = predictedRssiDbm - baselineRssiDbm

    /**
     * Whether signal is expected to improve
     */
    val expectsImprovement: Boolean
        get() = expectedChangeDbm > 0

    /**
     * Whether signal is expected to degrade
     */
    val expectsDegradation: Boolean
        get() = expectedChangeDbm < 0

    /**
     * Predicted signal strength category
     */
    val predictedStrength: SignalStrength
        get() = SignalStrength.fromRssi(predictedRssiDbm)

    /**
     * Human-readable summary
     */
    val summary: String
        get() =
            buildString {
                append("${predictedRssiDbm}dBm")
                if (expectedChangeDbm != 0) {
                    val sign = if (expectedChangeDbm > 0) "+" else ""
                    append(" (${sign}${expectedChangeDbm}dB)")
                }
                append(" [${confidence.displayName}]")
            }
}

/**
 * Network health prediction
 *
 * @property ssid Network name
 * @property predictionTimeMillis When this prediction is for
 * @property currentHealthScore Current health score
 * @property predictedHealthScore Predicted health score
 * @property currentHealth Current health category
 * @property predictedHealth Predicted health category
 * @property confidence Prediction confidence
 * @property trend Current trend direction
 */
data class NetworkHealthPrediction(
    val ssid: String,
    val predictionTimeMillis: Long,
    val currentHealthScore: Int,
    val predictedHealthScore: Int,
    val currentHealth: NetworkHealth,
    val predictedHealth: NetworkHealth,
    val confidence: PredictionConfidence,
    val trend: NetworkTrendDirection,
) {
    /**
     * Expected health score change
     */
    val expectedChange: Int
        get() = predictedHealthScore - currentHealthScore

    /**
     * Whether health is expected to improve
     */
    val expectsImprovement: Boolean
        get() = predictedHealth.minScore > currentHealth.minScore

    /**
     * Whether health is expected to degrade
     */
    val expectsDegradation: Boolean
        get() = predictedHealth.minScore < currentHealth.minScore

    /**
     * Human-readable summary
     */
    val summary: String
        get() =
            buildString {
                append("${currentHealth.displayName} → ${predictedHealth.displayName}")
                if (expectedChange != 0) {
                    val sign = if (expectedChange > 0) "+" else ""
                    append(" (${sign}$expectedChange)")
                }
            }
}

/**
 * Coverage quality prediction
 */
data class CoveragePrediction(
    val predictionTimeMillis: Long,
    val currentApCount: Int,
    val predictedApCount: Int,
    val currentAvgSignalDbm: Int,
    val predictedAvgSignalDbm: Int,
    val quality: CoverageQuality,
    val confidence: PredictionConfidence,
) {
    val expectsImprovement: Boolean
        get() = predictedApCount > currentApCount || predictedAvgSignalDbm > currentAvgSignalDbm

    val expectsDegradation: Boolean
        get() = predictedApCount < currentApCount || predictedAvgSignalDbm < currentAvgSignalDbm
}

/**
 * Connection time recommendation
 */
data class ConnectionTimeRecommendation(
    val bssid: String,
    val currentSignalDbm: Int,
    val bestTimeHoursAhead: Int,
    val bestPredictedSignalDbm: Int,
    val worstTimeHoursAhead: Int,
    val worstPredictedSignalDbm: Int,
    val recommendation: ConnectionTiming,
)

/**
 * Connection issues prediction
 */
data class IssuesPrediction(
    val bssid: String,
    val predictionTimeMillis: Long,
    val issueProbability: Double,
    val riskLevel: RiskLevel,
    val likelyIssues: List<String>,
    val signalPrediction: SignalPrediction,
) {
    val summary: String
        get() = "${riskLevel.displayName} risk (${(issueProbability * 100).toInt()}%)"
}

/**
 * Coverage quality categories
 */
enum class CoverageQuality(
    val displayName: String,
) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    FAIR("Fair"),
    POOR("Poor"),
}

/**
 * Connection timing recommendations
 */
enum class ConnectionTiming(
    val displayName: String,
) {
    /** Connect immediately - current conditions are good */
    IMMEDIATE("Connect Now"),

    /** Wait for better conditions */
    WAIT_FOR_IMPROVEMENT("Wait for Better Signal"),

    /** Flexible - conditions won't change significantly */
    FLEXIBLE("Flexible Timing"),
}

/**
 * Risk level categories
 */
enum class RiskLevel(
    val displayName: String,
) {
    /** High risk of connection issues */
    HIGH("High"),

    /** Medium risk */
    MEDIUM("Medium"),

    /** Low risk */
    LOW("Low"),

    /** Minimal risk */
    MINIMAL("Minimal"),
}
