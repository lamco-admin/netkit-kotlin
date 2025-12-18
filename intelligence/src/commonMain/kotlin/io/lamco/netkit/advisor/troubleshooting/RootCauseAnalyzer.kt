package io.lamco.netkit.advisor.troubleshooting

/**
 * Root cause analyzer using rule-based inference
 *
 * Applies expert rules to narrow down root causes from symptoms and evidence.
 * Uses forward chaining inference (NOT machine learning).
 *
 * Inference Process:
 * 1. **Fact collection**: Gather symptoms, metrics, context
 * 2. **Rule matching**: Find applicable rules based on facts
 * 3. **Forward chaining**: Apply rules to derive new facts
 * 4. **Confidence calculation**: Score each potential cause
 * 5. **Ranking**: Order causes by probability
 *
 * Rule Format:
 * ```
 * IF <conditions> THEN <conclusion> WITH <confidence>
 *
 * Example:
 * IF signal_strength < -75dBm AND disconnects > 5/hour
 * THEN cause = WEAK_SIGNAL WITH confidence = 0.9
 * ```
 *
 * Example Usage:
 * ```kotlin
 * val analyzer = RootCauseAnalyzer()
 *
 * val causes = analyzer.analyze(
 *     symptoms = listOf(Symptom.SlowSpeed(10.0, 100.0)),
 *     evidence = mapOf(
 *         "signal_strength" to "-72",
 *         "channel_util" to "85",
 *         "client_count" to "45"
 *     )
 * )
 *
 * causes.forEach { println("${it.cause}: ${it.probability}") }
 * ```
 */
class RootCauseAnalyzer {
    /**
     * Analyze symptoms and evidence to identify root causes
     *
     * @param symptoms List of observed symptoms
     * @param evidence Map of evidence facts (key-value pairs)
     * @param context Optional diagnostic context
     * @return List of root causes ordered by probability
     */
    fun analyze(
        symptoms: List<Symptom>,
        evidence: Map<String, String> = emptyMap(),
        context: DiagnosticContext = DiagnosticContext(),
    ): List<RootCause> {
        require(symptoms.isNotEmpty()) { "At least one symptom required for analysis" }

        val facts = buildFactBase(symptoms, evidence, context)

        val conclusions = applyInferenceRules(facts)

        return conclusions
            .map { conclusion ->
                RootCause(
                    cause = conclusion.issue,
                    probability = conclusion.confidence,
                    evidence = conclusion.supportingFacts,
                    fixSuggestion = getSuggestionForIssue(conclusion.issue),
                )
            }.sortedByDescending { it.probability }
    }

    /**
     * Perform deep analysis to find hidden causes
     *
     * Applies more complex inference chains to identify non-obvious issues.
     *
     * @param symptoms List of symptoms
     * @param context Diagnostic context
     * @return List of potential hidden root causes
     */
    fun deepAnalyze(
        symptoms: List<Symptom>,
        context: DiagnosticContext,
    ): List<RootCause> {
        val facts = buildFactBase(symptoms, emptyMap(), context)

        // Apply advanced inference rules (multi-step reasoning)
        val advancedConclusions = applyAdvancedRules(facts)

        return advancedConclusions
            .map { conclusion ->
                RootCause(
                    cause = conclusion.issue,
                    probability = conclusion.confidence,
                    evidence = conclusion.supportingFacts,
                    fixSuggestion = getSuggestionForIssue(conclusion.issue),
                )
            }.sortedByDescending { it.probability }
    }

    // ========================================
    // Private Inference Logic
    // ========================================

    /**
     * Build fact base from all available information
     */
    private fun buildFactBase(
        symptoms: List<Symptom>,
        evidence: Map<String, String>,
        context: DiagnosticContext,
    ): MutableSet<Fact> {
        val facts = mutableSetOf<Fact>()

        for (symptom in symptoms) {
            when (symptom) {
                is Symptom.SlowSpeed -> {
                    facts.add(Fact("symptom", "slow_speed"))
                    facts.add(Fact("speed_ratio", (symptom.reportedSpeed / symptom.expectedSpeed).toString()))
                }
                is Symptom.FrequentDisconnects -> {
                    facts.add(Fact("symptom", "frequent_disconnects"))
                    facts.add(Fact("disconnect_rate", symptom.disconnectsPerHour.toString()))
                }
                is Symptom.PoorCoverage -> {
                    facts.add(Fact("symptom", "poor_coverage"))
                    facts.add(Fact("signal_strength", symptom.signalStrength.toString()))
                }
                is Symptom.HighLatency -> {
                    facts.add(Fact("symptom", "high_latency"))
                    facts.add(Fact("latency_ms", symptom.latencyMs.toString()))
                }
                is Symptom.CannotConnect -> {
                    facts.add(Fact("symptom", "cannot_connect"))
                }
                is Symptom.IntermittentIssues -> {
                    facts.add(Fact("symptom", "intermittent"))
                    facts.add(Fact("frequency", symptom.frequency))
                }
                is Symptom.InterferenceDetected -> {
                    facts.add(Fact("symptom", "interference"))
                    facts.add(Fact("interference_level", symptom.interferenceLevel.toString()))
                }
                is Symptom.Custom -> {
                    facts.add(Fact("symptom", "custom"))
                }
            }
        }

        context.signalStrength?.let { facts.add(Fact("signal_strength", it.toString())) }
        context.channelUtilization?.let { facts.add(Fact("channel_util", it.toString())) }
        context.connectedClients?.let { facts.add(Fact("client_count", it.toString())) }
        context.band?.let { facts.add(Fact("band", it)) }
        context.channel?.let { facts.add(Fact("channel", it.toString())) }
        context.bandwidth?.let { facts.add(Fact("bandwidth", it.toString())) }
        context.latency?.let { facts.add(Fact("latency", it.toString())) }
        context.packetLoss?.let { facts.add(Fact("packet_loss", it.toString())) }

        for ((key, value) in evidence) {
            facts.add(Fact(key, value))
        }

        return facts
    }

    /**
     * Apply forward chaining inference rules
     */
    private fun applyInferenceRules(facts: Set<Fact>): List<Conclusion> {
        val conclusions = mutableListOf<Conclusion>()

        for (rule in INFERENCE_RULES) {
            if (rule.matches(facts)) {
                val conclusion = rule.apply(facts)
                conclusions.add(conclusion)
            }
        }

        return conclusions
    }

    /**
     * Apply advanced multi-step reasoning rules
     */
    private fun applyAdvancedRules(facts: Set<Fact>): List<Conclusion> {
        val conclusions = mutableListOf<Conclusion>()

        // Rule: If slow speed but good signal, likely channel congestion or AP overload
        if (hasFact(facts, "symptom", "slow_speed") &&
            getIntFact(facts, "signal_strength") ?: -100 > -65
        ) {
            if (getIntFact(facts, "channel_util") ?: 0 > 70) {
                conclusions.add(
                    Conclusion(
                        issue = NetworkIssue.CHANNEL_CONGESTION,
                        confidence = 0.85,
                        supportingFacts =
                            listOf(
                                "Slow speed despite good signal",
                                "High channel utilization indicates congestion",
                            ),
                    ),
                )
            }

            if (getIntFact(facts, "client_count") ?: 0 > 30) {
                conclusions.add(
                    Conclusion(
                        issue = NetworkIssue.AP_OVERLOADED,
                        confidence = 0.8,
                        supportingFacts =
                            listOf(
                                "Slow speed despite good signal",
                                "High client count indicates AP overload",
                            ),
                    ),
                )
            }
        }

        // Rule: If high disconnect rate on 2.4GHz, likely interference
        if (hasFact(facts, "symptom", "frequent_disconnects") &&
            hasFact(facts, "band", "2.4GHz")
        ) {
            conclusions.add(
                Conclusion(
                    issue = NetworkIssue.INTERFERENCE,
                    confidence = 0.75,
                    supportingFacts =
                        listOf(
                            "Frequent disconnects on congested 2.4GHz band",
                            "2.4GHz highly susceptible to interference",
                        ),
                ),
            )
        }

        // Rule: If intermittent issues during specific times, likely external factor
        if (hasFact(facts, "symptom", "intermittent") &&
            hasFact(facts, "frequency", "hourly")
        ) {
            conclusions.add(
                Conclusion(
                    issue = NetworkIssue.INTERFERENCE,
                    confidence = 0.7,
                    supportingFacts =
                        listOf(
                            "Periodic issues suggest external interference source",
                            "Hourly pattern indicates scheduled interference (e.g., microwave usage)",
                        ),
                ),
            )
        }

        return conclusions
    }

    /**
     * Check if fact base contains a specific fact
     */
    private fun hasFact(
        facts: Set<Fact>,
        attribute: String,
        value: String,
    ): Boolean = facts.any { it.attribute == attribute && it.value.equals(value, ignoreCase = true) }

    /**
     * Get integer value from fact base
     */
    private fun getIntFact(
        facts: Set<Fact>,
        attribute: String,
    ): Int? = facts.find { it.attribute == attribute }?.value?.toIntOrNull()

    /**
     * Get suggestion for network issue
     */
    private fun getSuggestionForIssue(issue: NetworkIssue): String =
        when (issue) {
            NetworkIssue.WRONG_PASSWORD -> "Verify WiFi password is correct"
            NetworkIssue.WEAK_SIGNAL -> "Move closer to AP or add additional APs"
            NetworkIssue.AP_OVERLOADED -> "Enable load balancing or add more APs"
            NetworkIssue.CHANNEL_CONGESTION -> "Switch to less congested channel"
            NetworkIssue.INTERFERENCE -> "Change channel or switch to 5GHz"
            NetworkIssue.BANDWIDTH_SATURATION -> "Limit background applications or upgrade bandwidth"
            NetworkIssue.HIGH_LATENCY -> "Check for interference and reduce congestion"
            NetworkIssue.PACKET_LOSS -> "Check signal quality and reduce interference"
            NetworkIssue.SLOW_DNS -> "Change DNS servers to faster alternatives (e.g., 8.8.8.8)"
            NetworkIssue.ISP_ISSUE -> "Contact ISP or check modem/router"
            NetworkIssue.INCORRECT_CHANNEL -> "Use WiFi analyzer to select optimal channel"
            NetworkIssue.SUBOPTIMAL_POWER -> "Adjust transmit power based on environment"
            NetworkIssue.POOR_AP_PLACEMENT -> "Relocate AP to more central location"
            NetworkIssue.MISSING_BAND_STEERING -> "Enable band steering to prefer 5GHz"
            NetworkIssue.OUTDATED_FIRMWARE -> "Update AP firmware to latest version"
            NetworkIssue.FAILING_HARDWARE -> "Check logs and replace failing hardware"
            NetworkIssue.INSUFFICIENT_COVERAGE -> "Add additional APs for better coverage"
            NetworkIssue.DEVICE_COMPATIBILITY -> "Check device supports AP security/protocols"
            NetworkIssue.WEAK_ENCRYPTION -> "Upgrade to WPA3 or minimum WPA2"
            NetworkIssue.UNAUTHORIZED_ACCESS -> "Enable MAC filtering or check security logs"
            NetworkIssue.DOS_ATTACK -> "Enable DoS protection and rate limiting"
            NetworkIssue.CLIENT_ROAMING_FAILURE -> "Enable 802.11r fast roaming"
            NetworkIssue.CLIENT_DRIVER_ISSUE -> "Update client device drivers"
            NetworkIssue.CLIENT_POWER_SAVE_ISSUE -> "Disable power save mode on client"
        }

    companion object {
        /**
         * Inference rules database
         */
        private val INFERENCE_RULES =
            listOf(
                // Signal strength rules
                InferenceRule(
                    name = "Weak Signal Detection",
                    conditions =
                        listOf(
                            Condition("signal_strength", ComparisonOp.LESS_THAN, "-70"),
                        ),
                    conclusion = NetworkIssue.WEAK_SIGNAL,
                    confidence = 0.85,
                ),
                InferenceRule(
                    name = "Very Weak Signal",
                    conditions =
                        listOf(
                            Condition("signal_strength", ComparisonOp.LESS_THAN, "-80"),
                        ),
                    conclusion = NetworkIssue.WEAK_SIGNAL,
                    confidence = 0.95,
                ),
                // Channel congestion rules
                InferenceRule(
                    name = "High Channel Utilization",
                    conditions =
                        listOf(
                            Condition("channel_util", ComparisonOp.GREATER_THAN, "70"),
                        ),
                    conclusion = NetworkIssue.CHANNEL_CONGESTION,
                    confidence = 0.75,
                ),
                InferenceRule(
                    name = "Severe Channel Congestion",
                    conditions =
                        listOf(
                            Condition("channel_util", ComparisonOp.GREATER_THAN, "85"),
                        ),
                    conclusion = NetworkIssue.CHANNEL_CONGESTION,
                    confidence = 0.9,
                ),
                // Client overload rules
                InferenceRule(
                    name = "High Client Count",
                    conditions =
                        listOf(
                            Condition("client_count", ComparisonOp.GREATER_THAN, "30"),
                        ),
                    conclusion = NetworkIssue.AP_OVERLOADED,
                    confidence = 0.7,
                ),
                InferenceRule(
                    name = "Severe Client Overload",
                    conditions =
                        listOf(
                            Condition("client_count", ComparisonOp.GREATER_THAN, "50"),
                        ),
                    conclusion = NetworkIssue.AP_OVERLOADED,
                    confidence = 0.9,
                ),
                // Interference rules
                InferenceRule(
                    name = "2.4GHz Interference",
                    conditions =
                        listOf(
                            Condition("band", ComparisonOp.EQUALS, "2.4GHz"),
                            Condition("symptom", ComparisonOp.EQUALS, "frequent_disconnects"),
                        ),
                    conclusion = NetworkIssue.INTERFERENCE,
                    confidence = 0.7,
                ),
                // Latency rules
                InferenceRule(
                    name = "High Latency Detection",
                    conditions =
                        listOf(
                            Condition("latency", ComparisonOp.GREATER_THAN, "100"),
                        ),
                    conclusion = NetworkIssue.HIGH_LATENCY,
                    confidence = 0.75,
                ),
            )
    }
}

/**
 * Fact in the fact base
 */
private data class Fact(
    val attribute: String,
    val value: String,
)

/**
 * Inference rule condition
 */
private data class Condition(
    val attribute: String,
    val operator: ComparisonOp,
    val value: String,
) {
    fun matches(facts: Set<Fact>): Boolean {
        val fact = facts.find { it.attribute == attribute } ?: return false

        return when (operator) {
            ComparisonOp.EQUALS -> fact.value.equals(value, ignoreCase = true)
            ComparisonOp.NOT_EQUALS -> !fact.value.equals(value, ignoreCase = true)
            ComparisonOp.GREATER_THAN -> {
                val factVal = fact.value.toDoubleOrNull() ?: return false
                val condVal = value.toDoubleOrNull() ?: return false
                factVal > condVal
            }
            ComparisonOp.LESS_THAN -> {
                val factVal = fact.value.toDoubleOrNull() ?: return false
                val condVal = value.toDoubleOrNull() ?: return false
                factVal < condVal
            }
            ComparisonOp.GREATER_OR_EQUAL -> {
                val factVal = fact.value.toDoubleOrNull() ?: return false
                val condVal = value.toDoubleOrNull() ?: return false
                factVal >= condVal
            }
            ComparisonOp.LESS_OR_EQUAL -> {
                val factVal = fact.value.toDoubleOrNull() ?: return false
                val condVal = value.toDoubleOrNull() ?: return false
                factVal <= condVal
            }
        }
    }
}

/**
 * Comparison operators for rule conditions
 */
private enum class ComparisonOp {
    EQUALS,
    NOT_EQUALS,
    GREATER_THAN,
    LESS_THAN,
    GREATER_OR_EQUAL,
    LESS_OR_EQUAL,
}

/**
 * Inference rule
 */
private data class InferenceRule(
    val name: String,
    val conditions: List<Condition>,
    val conclusion: NetworkIssue,
    val confidence: Double,
) {
    fun matches(facts: Set<Fact>): Boolean = conditions.all { it.matches(facts) }

    fun apply(facts: Set<Fact>): Conclusion {
        val supportingFacts =
            conditions.map { condition ->
                val fact = facts.find { it.attribute == condition.attribute }
                "${condition.attribute} ${condition.operator.name.lowercase().replace('_', ' ')} ${condition.value}" +
                    fact?.let { " (actual: ${it.value})" }.orEmpty()
            }

        return Conclusion(
            issue = conclusion,
            confidence = confidence,
            supportingFacts = supportingFacts,
        )
    }
}

/**
 * Inference conclusion
 */
private data class Conclusion(
    val issue: NetworkIssue,
    val confidence: Double,
    val supportingFacts: List<String>,
)
