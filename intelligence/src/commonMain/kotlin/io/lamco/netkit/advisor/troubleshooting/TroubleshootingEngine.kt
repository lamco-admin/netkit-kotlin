package io.lamco.netkit.advisor.troubleshooting

/**
 * Decision tree-based troubleshooting engine
 *
 * Analyzes network symptoms and diagnostic context to identify root causes
 * using a decision tree approach (NOT machine learning).
 *
 * The engine uses:
 * - **Decision trees**: Structured if-then-else logic
 * - **Rule-based inference**: Expert knowledge encoded as rules
 * - **Pattern matching**: Symptom correlation
 * - **Heuristics**: Experience-based shortcuts
 *
 * NOT USED:
 * - Machine learning models
 * - Training data
 * - Neural networks
 * - Statistical inference
 *
 * Decision Tree Example:
 * ```
 * IF cannot_connect THEN
 *   IF signal_weak THEN
 *     DIAGNOSE: weak_signal
 *   ELSE IF wrong_password THEN
 *     DIAGNOSE: authentication_failure
 *   ELSE
 *     DIAGNOSE: ap_issue
 * ```
 *
 * Example Usage:
 * ```kotlin
 * val engine = TroubleshootingEngine()
 *
 * val diagnosis = engine.diagnose(
 *     symptoms = listOf(Symptom.SlowSpeed(10.0, 100.0)),
 *     context = DiagnosticContext(
 *         signalStrength = -65,
 *         channelUtilization = 85,
 *         connectedClients = 45
 *     )
 * )
 *
 * println(diagnosis.primaryCause?.cause)
 * println(diagnosis.troubleshootingSteps)
 * ```
 *
 * @see DiagnosisResult
 * @see Symptom
 * @see DiagnosticContext
 */
class TroubleshootingEngine {

    /**
     * Diagnose network issues from symptoms and context
     *
     * Uses decision tree logic to analyze symptoms and network conditions,
     * identifying most likely root causes with confidence scores.
     *
     * @param symptoms List of observed symptoms
     * @param context Network diagnostic context (optional)
     * @return Diagnosis with root causes and troubleshooting steps
     */
    fun diagnose(
        symptoms: List<Symptom>,
        context: DiagnosticContext = DiagnosticContext()
    ): DiagnosisResult {
        require(symptoms.isNotEmpty()) { "At least one symptom required for diagnosis" }

        val rootCauses = mutableListOf<RootCause>()

        for (symptom in symptoms) {
            val causes = analyzeSymptom(symptom, context)
            rootCauses.addAll(causes)
        }

        val mergedCauses = mergeDuplicateCauses(rootCauses)
        val sortedCauses = mergedCauses.sortedByDescending { it.probability }

        val confidence = calculateDiagnosticConfidence(sortedCauses, symptoms, context)

        val steps = generateTroubleshootingSteps(sortedCauses, symptoms)

        val resolutionTime = estimateResolutionTime(sortedCauses, symptoms)

        return DiagnosisResult(
            symptoms = symptoms,
            rootCauses = sortedCauses,
            confidence = confidence,
            troubleshootingSteps = steps,
            estimatedResolutionTime = resolutionTime
        )
    }

    /**
     * Get troubleshooting steps for a specific issue
     *
     * @param issue Network issue
     * @return List of troubleshooting steps
     */
    fun getTroubleshootingSteps(issue: NetworkIssue): List<TroubleshootingStep> {
        return ISSUE_TROUBLESHOOTING_STEPS[issue] ?: emptyList()
    }

    // ========================================
    // Private Decision Tree Logic
    // ========================================

    /**
     * Analyze single symptom using decision tree
     */
    private fun analyzeSymptom(
        symptom: Symptom,
        context: DiagnosticContext
    ): List<RootCause> {
        return when (symptom) {
            is Symptom.SlowSpeed -> diagnoseSlowSpeed(symptom, context)
            is Symptom.FrequentDisconnects -> diagnoseDisconnects(symptom, context)
            is Symptom.PoorCoverage -> diagnosePoorCoverage(symptom, context)
            is Symptom.HighLatency -> diagnoseHighLatency(symptom, context)
            is Symptom.CannotConnect -> diagnoseCannotConnect(symptom, context)
            is Symptom.IntermittentIssues -> diagnoseIntermittent(symptom, context)
            is Symptom.InterferenceDetected -> diagnoseInterference(symptom, context)
            is Symptom.Custom -> diagnoseCustom(symptom, context)
        }
    }

    /**
     * Decision tree for slow speed symptom
     */
    private fun diagnoseSlowSpeed(
        symptom: Symptom.SlowSpeed,
        context: DiagnosticContext
    ): List<RootCause> {
        val causes = mutableListOf<RootCause>()

        if (context.signalStrength != null && context.signalStrength < -70) {
            causes.add(RootCause(
                cause = NetworkIssue.WEAK_SIGNAL,
                probability = 0.8,
                evidence = listOf(
                    "Signal strength ${context.signalStrength}dBm is below -70dBm threshold",
                    "Slow speed: ${symptom.reportedSpeed}Mbps vs expected ${symptom.expectedSpeed}Mbps"
                ),
                fixSuggestion = "Move closer to AP or add additional APs for better coverage"
            ))
        }

        if (context.channelUtilization != null && context.channelUtilization > 70) {
            causes.add(RootCause(
                cause = NetworkIssue.CHANNEL_CONGESTION,
                probability = 0.75,
                evidence = listOf(
                    "Channel utilization at ${context.channelUtilization}% (>70% threshold)",
                    "Multiple users competing for bandwidth"
                ),
                fixSuggestion = "Switch to less congested channel or upgrade to wider channels"
            ))
        }

        if (context.connectedClients != null && context.connectedClients > 30) {
            causes.add(RootCause(
                cause = NetworkIssue.AP_OVERLOADED,
                probability = 0.7,
                evidence = listOf(
                    "${context.connectedClients} clients connected (>30 threshold)",
                    "AP capacity exceeded"
                ),
                fixSuggestion = "Add additional APs or enable load balancing"
            ))
        }

        // WiFi metrics good suggests issue upstream (ISP or modem)
        if (context.signalStrength != null && context.signalStrength > -65 &&
            context.channelUtilization != null && context.channelUtilization < 50) {
            causes.add(RootCause(
                cause = NetworkIssue.ISP_ISSUE,
                probability = 0.6,
                evidence = listOf(
                    "WiFi metrics are good (signal: ${context.signalStrength}dBm, utilization: ${context.channelUtilization}%)",
                    "Issue likely upstream from WiFi network"
                ),
                fixSuggestion = "Contact ISP or check modem/router connection"
            ))
        }

        if (context.bandwidth != null && context.bandwidth < symptom.expectedSpeed * 0.3) {
            causes.add(RootCause(
                cause = NetworkIssue.BANDWIDTH_SATURATION,
                probability = 0.65,
                evidence = listOf(
                    "Available bandwidth ${context.bandwidth}Mbps << expected ${symptom.expectedSpeed}Mbps",
                    "Network congestion detected"
                ),
                fixSuggestion = "Limit background applications or upgrade bandwidth"
            ))
        }

        // No diagnostic context available - infer cause from symptom severity
        if (causes.isEmpty()) {
            val speedRatio = symptom.reportedSpeed / symptom.expectedSpeed
            val issue = when {
                speedRatio < 0.1 -> NetworkIssue.BANDWIDTH_SATURATION
                speedRatio < 0.3 -> NetworkIssue.CHANNEL_CONGESTION
                else -> NetworkIssue.WEAK_SIGNAL
            }
            causes.add(RootCause(
                cause = issue,
                probability = 0.5,
                evidence = listOf(
                    "Speed: ${symptom.reportedSpeed}Mbps vs expected ${symptom.expectedSpeed}Mbps (${(speedRatio * 100).toInt()}%)",
                    "Additional diagnostics needed for higher confidence"
                ),
                fixSuggestion = "Run signal strength test and check channel utilization for more accurate diagnosis"
            ))
        }

        return causes
    }

    /**
     * Decision tree for frequent disconnects
     */
    private fun diagnoseDisconnects(
        symptom: Symptom.FrequentDisconnects,
        context: DiagnosticContext
    ): List<RootCause> {
        val causes = mutableListOf<RootCause>()

        if (context.signalStrength != null && context.signalStrength < -75) {
            causes.add(RootCause(
                cause = NetworkIssue.WEAK_SIGNAL,
                probability = 0.85,
                evidence = listOf(
                    "Very weak signal: ${context.signalStrength}dBm",
                    "${symptom.disconnectsPerHour} disconnects per hour"
                ),
                fixSuggestion = "Improve AP placement or add APs for better coverage"
            ))
        }

        if (context.band == "2.4GHz") {
            causes.add(RootCause(
                cause = NetworkIssue.INTERFERENCE,
                probability = 0.7,
                evidence = listOf(
                    "Operating on congested 2.4GHz band",
                    "Frequent disconnects suggest interference"
                ),
                fixSuggestion = "Switch to 5GHz band or change 2.4GHz channel to 1, 6, or 11"
            ))
        }

        if (symptom.affectedDevices.isNotEmpty()) {
            causes.add(RootCause(
                cause = NetworkIssue.CLIENT_ROAMING_FAILURE,
                probability = 0.65,
                evidence = listOf(
                    "Specific devices affected: ${symptom.affectedDevices.joinToString()}",
                    "Possible roaming or fast transition issues"
                ),
                fixSuggestion = "Enable 802.11r fast roaming or adjust minimum RSSI thresholds"
            ))
        }

        if (symptom.disconnectsPerHour > 10) {
            causes.add(RootCause(
                cause = NetworkIssue.FAILING_HARDWARE,
                probability = 0.6,
                evidence = listOf(
                    "Very high disconnect rate: ${symptom.disconnectsPerHour}/hour",
                    "May indicate AP hardware failure"
                ),
                fixSuggestion = "Check AP logs, reboot AP, or replace if hardware failure suspected"
            ))
        }

        return causes
    }

    /**
     * Decision tree for poor coverage
     */
    private fun diagnosePoorCoverage(
        symptom: Symptom.PoorCoverage,
        context: DiagnosticContext
    ): List<RootCause> {
        return listOf(
            RootCause(
                cause = NetworkIssue.INSUFFICIENT_COVERAGE,
                probability = 0.9,
                evidence = listOf(
                    "Weak signal in ${symptom.location}: ${symptom.signalStrength}dBm",
                    "Coverage gap detected"
                ),
                fixSuggestion = "Add AP in ${symptom.location} or increase transmit power"
            ),
            RootCause(
                cause = NetworkIssue.POOR_AP_PLACEMENT,
                probability = 0.7,
                evidence = listOf(
                    "Signal strength ${symptom.signalStrength}dBm indicates poor AP positioning"
                ),
                fixSuggestion = "Relocate AP to more central location or remove physical obstructions"
            )
        )
    }

    /**
     * Decision tree for high latency
     */
    private fun diagnoseHighLatency(
        symptom: Symptom.HighLatency,
        context: DiagnosticContext
    ): List<RootCause> {
        val causes = mutableListOf<RootCause>()

        if (symptom.targetHost == "gateway" || symptom.targetHost.contains("local", ignoreCase = true)) {
            // High latency to local gateway = WiFi issue
            if (context.channelUtilization != null && context.channelUtilization > 80) {
                causes.add(RootCause(
                    cause = NetworkIssue.CHANNEL_CONGESTION,
                    probability = 0.8,
                    evidence = listOf(
                        "High latency to local gateway: ${symptom.latencyMs}ms",
                        "Channel utilization: ${context.channelUtilization}%"
                    ),
                    fixSuggestion = "Switch to less congested channel"
                ))
            }

            causes.add(RootCause(
                cause = NetworkIssue.HIGH_LATENCY,
                probability = 0.7,
                evidence = listOf("Latency to ${symptom.targetHost}: ${symptom.latencyMs}ms"),
                fixSuggestion = "Check for wireless interference and reduce channel congestion"
            ))
        } else {
            // High latency to internet = likely ISP issue
            causes.add(RootCause(
                cause = NetworkIssue.ISP_ISSUE,
                probability = 0.75,
                evidence = listOf(
                    "High latency to external host ${symptom.targetHost}: ${symptom.latencyMs}ms",
                    "Issue likely upstream from WiFi"
                ),
                fixSuggestion = "Contact ISP or check WAN connection"
            ))
        }

        return causes
    }

    /**
     * Decision tree for cannot connect
     */
    private fun diagnoseCannotConnect(
        symptom: Symptom.CannotConnect,
        context: DiagnosticContext
    ): List<RootCause> {
        val causes = mutableListOf<RootCause>()

        if (symptom.errorMessage?.contains("password", ignoreCase = true) == true ||
            symptom.errorMessage?.contains("authentication", ignoreCase = true) == true) {
            causes.add(RootCause(
                cause = NetworkIssue.WRONG_PASSWORD,
                probability = 0.95,
                evidence = listOf("Authentication error: ${symptom.errorMessage}"),
                fixSuggestion = "Verify WiFi password is correct"
            ))
        }

        if (context.signalStrength != null && context.signalStrength < -80) {
            causes.add(RootCause(
                cause = NetworkIssue.WEAK_SIGNAL,
                probability = 0.85,
                evidence = listOf(
                    "Extremely weak signal: ${context.signalStrength}dBm",
                    "Below minimum connection threshold"
                ),
                fixSuggestion = "Move closer to AP or improve coverage"
            ))
        }

        if (symptom.deviceType != null) {
            causes.add(RootCause(
                cause = NetworkIssue.DEVICE_COMPATIBILITY,
                probability = 0.6,
                evidence = listOf("Connection issue with ${symptom.deviceType}"),
                fixSuggestion = "Check device compatibility with AP security/protocols"
            ))
        }

        return causes
    }

    /**
     * Decision tree for intermittent issues
     */
    private fun diagnoseIntermittent(
        symptom: Symptom.IntermittentIssues,
        context: DiagnosticContext
    ): List<RootCause> {
        return listOf(
            RootCause(
                cause = NetworkIssue.INTERFERENCE,
                probability = 0.75,
                evidence = listOf(
                    "Intermittent issues (${symptom.frequency}) suggest periodic interference"
                ),
                fixSuggestion = "Scan for interference sources and change channel"
            ),
            RootCause(
                cause = NetworkIssue.FAILING_HARDWARE,
                probability = 0.6,
                evidence = listOf("Intermittent failures may indicate hardware degradation"),
                fixSuggestion = "Check AP logs and consider hardware replacement"
            )
        )
    }

    /**
     * Decision tree for detected interference
     */
    private fun diagnoseInterference(
        symptom: Symptom.InterferenceDetected,
        context: DiagnosticContext
    ): List<RootCause> {
        return listOf(
            RootCause(
                cause = NetworkIssue.INTERFERENCE,
                probability = 0.9,
                evidence = listOf(
                    "Interference detected on ${symptom.band}: ${(symptom.interferenceLevel * 100).toInt()}%"
                ),
                fixSuggestion = if (symptom.band == "2.4GHz") {
                    "Switch to 5GHz or change 2.4GHz channel to 1, 6, or 11"
                } else {
                    "Change 5GHz channel or enable DFS channels"
                }
            )
        )
    }

    /**
     * Decision tree for custom symptoms
     */
    private fun diagnoseCustom(
        symptom: Symptom.Custom,
        context: DiagnosticContext
    ): List<RootCause> {
        return listOf(
            RootCause(
                cause = NetworkIssue.CHANNEL_CONGESTION,
                probability = 0.5,
                evidence = listOf("Custom symptom: ${symptom.description}"),
                fixSuggestion = "Run comprehensive network diagnostics"
            )
        )
    }

    /**
     * Merge duplicate root causes and average probabilities
     */
    private fun mergeDuplicateCauses(causes: List<RootCause>): List<RootCause> {
        val grouped = causes.groupBy { it.cause }
        return grouped.map { (cause, causeList) ->
            if (causeList.size == 1) {
                causeList.first()
            } else {
                val avgProbability = causeList.map { it.probability }.average()
                val allEvidence = causeList.flatMap { it.evidence }.distinct()
                val fixSuggestion = causeList.first().fixSuggestion

                RootCause(
                    cause = cause,
                    probability = avgProbability,
                    evidence = allEvidence,
                    fixSuggestion = fixSuggestion
                )
            }
        }
    }

    /**
     * Calculate overall diagnostic confidence
     */
    private fun calculateDiagnosticConfidence(
        causes: List<RootCause>,
        symptoms: List<Symptom>,
        context: DiagnosticContext
    ): Double {
        if (causes.isEmpty()) return 0.0

        var confidence = causes.first().probability

        // Boost confidence if we have context data
        val contextFactors = listOfNotNull(
            context.signalStrength,
            context.channelUtilization,
            context.connectedClients,
            context.bandwidth
        ).size

        val contextBoost = contextFactors * 0.05  // +5% per context factor
        confidence = (confidence + contextBoost).coerceAtMost(1.0)

        return confidence
    }

    /**
     * Generate troubleshooting steps from root causes
     */
    private fun generateTroubleshootingSteps(
        causes: List<RootCause>,
        symptoms: List<Symptom>
    ): List<TroubleshootingStep> {
        if (causes.isEmpty()) return emptyList()

        val primarySteps = ISSUE_TROUBLESHOOTING_STEPS[causes.first().cause] ?: emptyList()

        return primarySteps.take(5)
    }

    /**
     * Estimate resolution time based on issues
     */
    private fun estimateResolutionTime(
        causes: List<RootCause>,
        symptoms: List<Symptom>
    ): String {
        if (causes.isEmpty()) return "Unknown"

        val primaryCause = causes.first().cause

        return when (primaryCause) {
            NetworkIssue.WRONG_PASSWORD -> "1-2 minutes"
            NetworkIssue.INCORRECT_CHANNEL -> "5-10 minutes"
            NetworkIssue.SUBOPTIMAL_POWER -> "5-10 minutes"
            NetworkIssue.WEAK_SIGNAL -> "15-30 minutes (AP repositioning)"
            NetworkIssue.OUTDATED_FIRMWARE -> "20-45 minutes"
            NetworkIssue.INSUFFICIENT_COVERAGE -> "1-2 hours (add APs)"
            NetworkIssue.FAILING_HARDWARE -> "2-4 hours (hardware replacement)"
            else -> "30-60 minutes"
        }
    }

    companion object {
        /**
         * Troubleshooting steps database for each issue
         */
        private val ISSUE_TROUBLESHOOTING_STEPS = mapOf(
            NetworkIssue.WEAK_SIGNAL to listOf(
                TroubleshootingStep(
                    step = 1,
                    action = "Check signal strength at problem location",
                    expectedOutcome = "Identify exact signal level (dBm)",
                    ifSuccess = "If < -70dBm, proceed to step 2",
                    ifFailure = "If > -70dBm, issue is not signal strength",
                    estimatedTime = "2 minutes"
                ),
                TroubleshootingStep(
                    step = 2,
                    action = "Move closer to AP or remove physical obstructions",
                    expectedOutcome = "Signal improves to > -65dBm",
                    ifSuccess = "Issue resolved",
                    ifFailure = "Proceed to step 3",
                    estimatedTime = "5 minutes"
                ),
                TroubleshootingStep(
                    step = 3,
                    action = "Increase AP transmit power (if adjustable)",
                    expectedOutcome = "Signal improves at problem location",
                    ifSuccess = "Issue resolved",
                    ifFailure = "Add additional AP for coverage",
                    estimatedTime = "10 minutes"
                )
            ),

            NetworkIssue.CHANNEL_CONGESTION to listOf(
                TroubleshootingStep(
                    step = 1,
                    action = "Scan for channel utilization on current and adjacent channels",
                    expectedOutcome = "Identify less congested channels",
                    ifSuccess = "Found channel with <50% utilization",
                    ifFailure = "All channels congested",
                    estimatedTime = "3 minutes"
                ),
                TroubleshootingStep(
                    step = 2,
                    action = "Switch to least congested channel (2.4GHz: 1, 6, or 11)",
                    expectedOutcome = "Performance improves",
                    ifSuccess = "Issue resolved",
                    ifFailure = "Proceed to step 3",
                    estimatedTime = "5 minutes"
                ),
                TroubleshootingStep(
                    step = 3,
                    action = "Switch to 5GHz band or enable DFS channels",
                    expectedOutcome = "Access to more channels",
                    ifSuccess = "Issue resolved",
                    ifFailure = "Consider additional APs",
                    estimatedTime = "10 minutes"
                )
            ),

            NetworkIssue.WRONG_PASSWORD to listOf(
                TroubleshootingStep(
                    step = 1,
                    action = "Verify WiFi password is correct",
                    expectedOutcome = "Able to connect",
                    ifSuccess = "Issue resolved",
                    ifFailure = "Reset network password",
                    estimatedTime = "1 minute"
                )
            ),

            NetworkIssue.AP_OVERLOADED to listOf(
                TroubleshootingStep(
                    step = 1,
                    action = "Check number of connected clients",
                    expectedOutcome = "Determine if >30 clients per AP",
                    ifSuccess = "Proceed to step 2",
                    ifFailure = "Issue is not client overload",
                    estimatedTime = "2 minutes"
                ),
                TroubleshootingStep(
                    step = 2,
                    action = "Enable client load balancing and band steering",
                    expectedOutcome = "Clients distributed across APs/bands",
                    ifSuccess = "Issue resolved",
                    ifFailure = "Add additional APs",
                    estimatedTime = "10 minutes"
                )
            )
        )
    }
}
