package io.lamco.netkit.advisor.troubleshooting

/**
 * Symptom pattern matcher
 *
 * Identifies common symptom patterns and correlations to improve diagnosis.
 * Uses pattern recognition (NOT machine learning).
 *
 * Pattern Types:
 * - **Symptom combinations**: Multiple symptoms indicating specific issues
 * - **Temporal patterns**: Time-based symptom occurrence
 * - **Correlation patterns**: Related symptoms appearing together
 *
 * Example Usage:
 * ```kotlin
 * val matcher = SymptomMatcher()
 *
 * val patterns = matcher.matchPatterns(
 *     listOf(
 *         Symptom.SlowSpeed(10.0, 100.0),
 *         Symptom.HighLatency(250, "gateway")
 *     )
 * )
 *
 * patterns.forEach { println(it.pattern.name) }
 * ```
 */
class SymptomMatcher {

    /**
     * Match symptom patterns
     *
     * @param symptoms List of symptoms to analyze
     * @return List of matched patterns with confidence scores
     */
    fun matchPatterns(symptoms: List<Symptom>): List<SymptomPatternMatch> {
        require(symptoms.isNotEmpty()) { "At least one symptom required" }

        val matches = mutableListOf<SymptomPatternMatch>()

        for (pattern in SYMPTOM_PATTERNS) {
            val match = pattern.matches(symptoms)
            if (match != null) {
                matches.add(match)
            }
        }

        return matches.sortedByDescending { it.confidence }
    }

    /**
     * Find correlated symptoms
     *
     * Identifies symptoms that commonly appear together.
     *
     * @param primarySymptom Primary symptom
     * @param allSymptoms All observed symptoms
     * @return List of correlated symptoms
     */
    fun findCorrelatedSymptoms(
        primarySymptom: Symptom,
        allSymptoms: List<Symptom>
    ): List<SymptomCorrelation> {
        val correlations = mutableListOf<SymptomCorrelation>()

        when (primarySymptom) {
            is Symptom.SlowSpeed -> {
                // Slow speed often correlates with high latency
                allSymptoms.filterIsInstance<Symptom.HighLatency>().forEach {
                    correlations.add(SymptomCorrelation(
                        symptom1 = primarySymptom,
                        symptom2 = it,
                        correlation = 0.85,
                        explanation = "Slow speed and high latency often indicate network congestion"
                    ))
                }

                // Slow speed with poor coverage indicates signal issues
                allSymptoms.filterIsInstance<Symptom.PoorCoverage>().forEach {
                    correlations.add(SymptomCorrelation(
                        symptom1 = primarySymptom,
                        symptom2 = it,
                        correlation = 0.8,
                        explanation = "Slow speed with weak signal indicates coverage problem"
                    ))
                }
            }

            is Symptom.FrequentDisconnects -> {
                // Disconnects with poor coverage = signal issue
                allSymptoms.filterIsInstance<Symptom.PoorCoverage>().forEach {
                    correlations.add(SymptomCorrelation(
                        symptom1 = primarySymptom,
                        symptom2 = it,
                        correlation = 0.9,
                        explanation = "Frequent disconnects with weak signal strongly indicates coverage issue"
                    ))
                }

                allSymptoms.filterIsInstance<Symptom.InterferenceDetected>().forEach {
                    correlations.add(SymptomCorrelation(
                        symptom1 = primarySymptom,
                        symptom2 = it,
                        correlation = 0.85,
                        explanation = "Disconnects with interference suggest RF environment problems"
                    ))
                }
            }

            else -> {}
        }

        return correlations.sortedByDescending { it.correlation }
    }

    /**
     * Get severity level for symptom combination
     *
     * @param symptoms List of symptoms
     * @return Overall severity (1-10)
     */
    fun calculateCombinedSeverity(symptoms: List<Symptom>): Int {
        if (symptoms.isEmpty()) return 0

        // Use weighted average with emphasis on highest severity
        val severities = symptoms.map { it.severity }
        val maxSeverity = severities.maxOrNull() ?: 0
        val avgSeverity = severities.average()

        // 70% weight on max, 30% on average
        return ((maxSeverity * 0.7 + avgSeverity * 0.3).toInt()).coerceIn(1, 10)
    }

    companion object {
        /**
         * Pre-defined symptom patterns
         */
        private val SYMPTOM_PATTERNS = listOf(
            // Pattern: WiFi Dead Zone
            SymptomPattern(
                name = "WiFi Dead Zone",
                description = "Area with no WiFi coverage or very weak signal",
                requiredSymptoms = listOf(
                    SymptomPattern.SymptomType.POOR_COVERAGE,
                    SymptomPattern.SymptomType.CANNOT_CONNECT
                ),
                confidence = 0.95,
                likelyIssues = listOf(NetworkIssue.INSUFFICIENT_COVERAGE, NetworkIssue.POOR_AP_PLACEMENT)
            ),

            // Pattern: Network Congestion
            SymptomPattern(
                name = "Network Congestion",
                description = "Network overloaded with traffic",
                requiredSymptoms = listOf(
                    SymptomPattern.SymptomType.SLOW_SPEED,
                    SymptomPattern.SymptomType.HIGH_LATENCY
                ),
                confidence = 0.85,
                likelyIssues = listOf(NetworkIssue.CHANNEL_CONGESTION, NetworkIssue.AP_OVERLOADED)
            ),

            // Pattern: Signal Degradation
            SymptomPattern(
                name = "Signal Degradation",
                description = "Weak signal causing connectivity issues",
                requiredSymptoms = listOf(
                    SymptomPattern.SymptomType.POOR_COVERAGE,
                    SymptomPattern.SymptomType.FREQUENT_DISCONNECTS
                ),
                confidence = 0.9,
                likelyIssues = listOf(NetworkIssue.WEAK_SIGNAL, NetworkIssue.INSUFFICIENT_COVERAGE)
            ),

            // Pattern: RF Interference
            SymptomPattern(
                name = "RF Interference",
                description = "External interference affecting WiFi",
                requiredSymptoms = listOf(
                    SymptomPattern.SymptomType.INTERFERENCE,
                    SymptomPattern.SymptomType.FREQUENT_DISCONNECTS
                ),
                confidence = 0.9,
                likelyIssues = listOf(NetworkIssue.INTERFERENCE)
            ),

            // Pattern: Intermittent Connectivity
            SymptomPattern(
                name = "Intermittent Connectivity",
                description = "Periodic connection problems",
                requiredSymptoms = listOf(
                    SymptomPattern.SymptomType.INTERMITTENT,
                    SymptomPattern.SymptomType.FREQUENT_DISCONNECTS
                ),
                confidence = 0.8,
                likelyIssues = listOf(NetworkIssue.INTERFERENCE, NetworkIssue.FAILING_HARDWARE)
            )
        )
    }
}

/**
 * Symptom pattern definition
 */
data class SymptomPattern(
    val name: String,
    val description: String,
    val requiredSymptoms: List<SymptomType>,
    val confidence: Double,
    val likelyIssues: List<NetworkIssue>
) {
    /**
     * Check if symptoms match this pattern
     */
    fun matches(symptoms: List<Symptom>): SymptomPatternMatch? {
        val symptomTypes = symptoms.map { it.toType() }

        val matchedCount = requiredSymptoms.count { it in symptomTypes }
        val matchRatio = matchedCount.toDouble() / requiredSymptoms.size

        return if (matchRatio >= 0.5) {  // At least 50% of required symptoms
            SymptomPatternMatch(
                pattern = this,
                matchedSymptoms = symptoms.filter { it.toType() in requiredSymptoms },
                confidence = confidence * matchRatio
            )
        } else {
            null
        }
    }

    /**
     * Symptom type enumeration
     */
    enum class SymptomType {
        SLOW_SPEED,
        FREQUENT_DISCONNECTS,
        POOR_COVERAGE,
        HIGH_LATENCY,
        CANNOT_CONNECT,
        INTERMITTENT,
        INTERFERENCE,
        CUSTOM
    }
}

/**
 * Convert symptom to type
 */
private fun Symptom.toType(): SymptomPattern.SymptomType {
    return when (this) {
        is Symptom.SlowSpeed -> SymptomPattern.SymptomType.SLOW_SPEED
        is Symptom.FrequentDisconnects -> SymptomPattern.SymptomType.FREQUENT_DISCONNECTS
        is Symptom.PoorCoverage -> SymptomPattern.SymptomType.POOR_COVERAGE
        is Symptom.HighLatency -> SymptomPattern.SymptomType.HIGH_LATENCY
        is Symptom.CannotConnect -> SymptomPattern.SymptomType.CANNOT_CONNECT
        is Symptom.IntermittentIssues -> SymptomPattern.SymptomType.INTERMITTENT
        is Symptom.InterferenceDetected -> SymptomPattern.SymptomType.INTERFERENCE
        is Symptom.Custom -> SymptomPattern.SymptomType.CUSTOM
    }
}

/**
 * Matched symptom pattern
 */
data class SymptomPatternMatch(
    val pattern: SymptomPattern,
    val matchedSymptoms: List<Symptom>,
    val confidence: Double
) {
    /**
     * Summary of the match
     */
    val summary: String
        get() = "${pattern.name} (${(confidence * 100).toInt()}% confidence)"
}

/**
 * Symptom correlation
 */
data class SymptomCorrelation(
    val symptom1: Symptom,
    val symptom2: Symptom,
    val correlation: Double,  // 0.0-1.0
    val explanation: String
) {
    init {
        require(correlation in 0.0..1.0) { "Correlation must be between 0.0 and 1.0" }
    }

    /**
     * Whether correlation is strong (>= 0.7)
     */
    val isStrongCorrelation: Boolean
        get() = correlation >= 0.7
}
