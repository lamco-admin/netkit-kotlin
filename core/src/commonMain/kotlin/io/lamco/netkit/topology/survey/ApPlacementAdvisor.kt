package io.lamco.netkit.topology.survey

/**
 * AP placement optimization advisor
 *
 * Analyzes site survey data and coverage heatmaps to recommend optimal
 * access point placements. Considers coverage gaps, signal strength,
 * interference, and deployment constraints.
 *
 * Recommendations include:
 * - New AP locations to fill coverage gaps
 * - Existing AP repositioning for better coverage
 * - Power adjustments to optimize signal distribution
 * - Channel assignments to minimize interference
 *
 * @property targetCoveragePercentage Desired coverage percentage (default: 95%)
 * @property minSignalThresholdDbm Minimum acceptable signal strength (default: -70)
 * @property maxApDistance Maximum recommended distance between APs (meters)
 * @property preferCentralLocations Whether to prefer central placement
 */
class ApPlacementAdvisor(
    val targetCoveragePercentage: Double = 95.0,
    val minSignalThresholdDbm: Int = -70,
    val maxApDistance: Double = 30.0,
    val preferCentralLocations: Boolean = true,
) {
    init {
        require(targetCoveragePercentage in 0.0..100.0) {
            "Target coverage percentage must be in [0, 100], got $targetCoveragePercentage"
        }
        require(minSignalThresholdDbm in -120..0) {
            "Min signal threshold must be in [-120, 0], got $minSignalThresholdDbm"
        }
        require(maxApDistance > 0) {
            "Max AP distance must be positive, got $maxApDistance"
        }
    }

    /**
     * Analyze survey and recommend AP placements
     *
     * Returns comprehensive recommendations for new APs, repositioning,
     * and configuration changes.
     */
    fun analyzeAndRecommend(
        session: SiteSurveySession,
        existingAps: List<ApLocation> = emptyList(),
    ): PlacementRecommendation {
        val heatmapper = CoverageHeatmapper()
        val deadZoneDetector = DeadZoneDetector()

        // Generate combined coverage heatmap
        val combinedHeatmap = heatmapper.generateCombinedHeatmap(session)
        val coverageStats =
            heatmapper.calculateCoverageStats(
                heatmapper.generateHeatmap(
                    session,
                    session.measurements
                        .first()
                        .snapshot.allBssids
                        .first()
                        .bssid,
                )
                    ?: return createEmptyRecommendation(),
            )

        // Detect dead zones
        val deadZones = deadZoneDetector.detectDeadZonesInCombined(combinedHeatmap)

        // Generate recommendations
        val newApRecommendations = recommendNewApPlacements(deadZones, combinedHeatmap, existingAps)
        val repositioningRecommendations = recommendRepositioning(existingAps, combinedHeatmap, deadZones)
        val powerAdjustments = recommendPowerAdjustments(existingAps, combinedHeatmap)
        val channelRecommendations = recommendChannelAssignments(existingAps)

        val overallScore =
            calculatePlacementScore(
                coverageStats,
                deadZones,
                existingAps,
            )

        return PlacementRecommendation(
            currentCoveragePercentage = coverageStats.coveragePercentage,
            targetCoveragePercentage = targetCoveragePercentage,
            deadZoneCount = deadZones.size,
            newApPlacements = newApRecommendations,
            repositioningRecommendations = repositioningRecommendations,
            powerAdjustments = powerAdjustments,
            channelAssignments = channelRecommendations,
            overallScore = overallScore,
            estimatedCostLevel = estimateCostLevel(newApRecommendations, repositioningRecommendations),
        )
    }

    /**
     * Recommend new AP placements
     */
    private fun recommendNewApPlacements(
        deadZones: List<DeadZone>,
        heatmap: CombinedHeatmap,
        existingAps: List<ApLocation>,
    ): List<ApPlacementSuggestion> {
        val suggestions = mutableListOf<ApPlacementSuggestion>()

        // Focus on critical and high severity dead zones
        val priorityZones =
            deadZones
                .filter { it.severity in listOf(DeadZoneSeverity.CRITICAL, DeadZoneSeverity.HIGH) }
                .sortedByDescending { it.cellCount }

        priorityZones.forEach { zone ->
            // Check if zone is already covered by suggesting repositioning instead
            val nearbyAp =
                existingAps.minByOrNull {
                    it.location.distanceMeters(zone.centerLocation)
                }

            if (nearbyAp != null && nearbyAp.location.distanceMeters(zone.centerLocation) < maxApDistance / 2) {
                // Too close to existing AP - will handle in repositioning
                return@forEach
            }

            // Suggest new AP placement
            suggestions.add(
                ApPlacementSuggestion(
                    location = zone.centerLocation,
                    priority = zone.severity.priority,
                    reasoning = "Fill ${zone.severity.displayName.lowercase()} dead zone (${zone.cellCount} cells)",
                    expectedImpact =
                        when (zone.severity) {
                            DeadZoneSeverity.CRITICAL -> PlacementImpact.CRITICAL
                            DeadZoneSeverity.HIGH -> PlacementImpact.HIGH
                            else -> PlacementImpact.MEDIUM
                        },
                    estimatedCoverage = estimateCoverageImprovement(zone),
                ),
            )
        }

        return suggestions.take(5) // Limit to top 5 recommendations
    }

    /**
     * Recommend repositioning existing APs
     */
    private fun recommendRepositioning(
        existingAps: List<ApLocation>,
        heatmap: CombinedHeatmap,
        deadZones: List<DeadZone>,
    ): List<RepositioningSuggestion> {
        val suggestions = mutableListOf<RepositioningSuggestion>()

        existingAps.forEach { ap ->
            // Find nearby dead zones
            val nearbyDeadZones =
                deadZones.filter { zone ->
                    ap.location.distanceMeters(zone.centerLocation) < maxApDistance
                }

            if (nearbyDeadZones.isEmpty()) return@forEach

            // Find optimal position to cover nearby dead zones
            val optimalPosition = calculateOptimalPosition(ap, nearbyDeadZones)

            val distance = ap.location.distanceMeters(optimalPosition)
            if (distance >= 2.0) { // Only suggest if move is significant (>= 2m)
                suggestions.add(
                    RepositioningSuggestion(
                        currentApId = ap.id,
                        currentLocation = ap.location,
                        suggestedLocation = optimalPosition,
                        distanceMeters = distance,
                        priority = nearbyDeadZones.maxOf { it.severity.priority },
                        reasoning = "Move closer to ${nearbyDeadZones.size} dead zone(s)",
                        expectedImpact = PlacementImpact.MEDIUM,
                    ),
                )
            }
        }

        return suggestions.sortedByDescending { it.priority }
    }

    /**
     * Calculate optimal position between current AP and dead zones
     */
    private fun calculateOptimalPosition(
        ap: ApLocation,
        deadZones: List<DeadZone>,
    ): SurveyLocation {
        if (deadZones.isEmpty()) return ap.location

        // Weight towards larger/more severe dead zones
        var weightedX = ap.location.x
        var weightedY = ap.location.y
        var totalWeight = 1.0

        deadZones.forEach { zone ->
            val weight = zone.cellCount.toDouble() * zone.severity.priority
            weightedX += zone.centerLocation.x * weight
            weightedY += zone.centerLocation.y * weight
            totalWeight += weight
        }

        return SurveyLocation(
            x = weightedX / totalWeight,
            y = weightedY / totalWeight,
            label = "Optimized position",
        )
    }

    /**
     * Recommend power adjustments for existing APs
     */
    private fun recommendPowerAdjustments(
        existingAps: List<ApLocation>,
        heatmap: CombinedHeatmap,
    ): List<PowerAdjustment> {
        val adjustments = mutableListOf<PowerAdjustment>()

        // Check for APs with overlapping coverage (suggest reducing power)
        existingAps.forEach { ap1 ->
            val nearbyAps =
                existingAps.filter { ap2 ->
                    ap2.id != ap1.id && ap1.location.distanceMeters(ap2.location) < maxApDistance / 2
                }

            if (nearbyAps.isNotEmpty()) {
                adjustments.add(
                    PowerAdjustment(
                        apId = ap1.id,
                        location = ap1.location,
                        currentPowerDbm = ap1.txPowerDbm,
                        suggestedPowerDbm = (ap1.txPowerDbm - 3).coerceAtLeast(0),
                        reasoning = "Reduce interference with ${nearbyAps.size} nearby AP(s)",
                        expectedImpact = PlacementImpact.LOW,
                    ),
                )
            }
        }

        return adjustments
    }

    /**
     * Recommend channel assignments
     */
    private fun recommendChannelAssignments(existingAps: List<ApLocation>): List<ChannelAssignment> {
        val assignments = mutableListOf<ChannelAssignment>()

        // Group APs by proximity for channel planning
        val apGroups = groupApsByProximity(existingAps)

        apGroups.forEach { group ->
            // Assign non-overlapping channels within group
            val channels = assignNonOverlappingChannels(group.size)

            group.forEachIndexed { index, ap ->
                assignments.add(
                    ChannelAssignment(
                        apId = ap.id,
                        location = ap.location,
                        currentChannel = ap.channel,
                        suggestedChannel = channels[index],
                        reasoning = "Minimize interference within AP group",
                        expectedImpact = PlacementImpact.MEDIUM,
                    ),
                )
            }
        }

        return assignments
    }

    /**
     * Group APs by proximity for channel planning
     */
    private fun groupApsByProximity(aps: List<ApLocation>): List<List<ApLocation>> {
        if (aps.isEmpty()) return emptyList()

        val groups = mutableListOf<MutableList<ApLocation>>()
        val assigned = mutableSetOf<String>()

        aps.forEach { ap ->
            if (ap.id in assigned) return@forEach

            val group = mutableListOf(ap)
            assigned.add(ap.id)

            // Find all APs within maxApDistance
            aps.forEach { other ->
                if (other.id !in assigned && ap.location.distanceMeters(other.location) < maxApDistance) {
                    group.add(other)
                    assigned.add(other.id)
                }
            }

            groups.add(group)
        }

        return groups
    }

    /**
     * Assign non-overlapping channels for 2.4 GHz band
     */
    private fun assignNonOverlappingChannels(count: Int): List<Int> {
        val nonOverlapping = listOf(1, 6, 11)
        return List(count) { nonOverlapping[it % 3] }
    }

    /**
     * Estimate coverage improvement from new AP
     */
    private fun estimateCoverageImprovement(deadZone: DeadZone): Double {
        // Simplified estimation: assume AP covers ~80m² effectively
        val estimatedCoverage = 80.0
        val zoneArea = deadZone.cellCount * 4.0 // Assuming ~2m cell size
        return (zoneArea / estimatedCoverage * 100.0).coerceAtMost(100.0)
    }

    /**
     * Calculate overall placement quality score
     */
    private fun calculatePlacementScore(
        coverage: CoverageStatistics,
        deadZones: List<DeadZone>,
        existingAps: List<ApLocation>,
    ): Int {
        var score = 100

        // Deduct for coverage below target
        if (coverage.coveragePercentage < targetCoveragePercentage) {
            score -= ((targetCoveragePercentage - coverage.coveragePercentage) * 2).toInt()
        }

        // Deduct for dead zones
        score -= deadZones.count { it.severity == DeadZoneSeverity.CRITICAL } * 20
        score -= deadZones.count { it.severity == DeadZoneSeverity.HIGH } * 10
        score -= deadZones.count { it.severity == DeadZoneSeverity.MEDIUM } * 5

        // Bonus for good AP density
        if (existingAps.size >= 3) score += 5

        return score.coerceIn(0, 100)
    }

    /**
     * Estimate implementation cost level
     */
    private fun estimateCostLevel(
        newAps: List<ApPlacementSuggestion>,
        repositioning: List<RepositioningSuggestion>,
    ): CostLevel {
        val newApCount = newAps.size
        val majorReposition = repositioning.count { it.distanceMeters >= 5.0 }

        return when {
            newApCount >= 3 || majorReposition >= 3 -> CostLevel.HIGH
            newApCount >= 1 || majorReposition >= 1 -> CostLevel.MEDIUM
            repositioning.isNotEmpty() -> CostLevel.LOW
            else -> CostLevel.MINIMAL
        }
    }

    /**
     * Create empty recommendation when insufficient data
     */
    private fun createEmptyRecommendation() =
        PlacementRecommendation(
            currentCoveragePercentage = 0.0,
            targetCoveragePercentage = targetCoveragePercentage,
            deadZoneCount = 0,
            newApPlacements = emptyList(),
            repositioningRecommendations = emptyList(),
            powerAdjustments = emptyList(),
            channelAssignments = emptyList(),
            overallScore = 0,
            estimatedCostLevel = CostLevel.MINIMAL,
        )
}

/**
 * Existing AP location
 *
 * @property id AP identifier
 * @property location Physical location
 * @property channel Current channel
 * @property txPowerDbm Transmit power in dBm
 */
data class ApLocation(
    val id: String,
    val location: SurveyLocation,
    val channel: Int = 6,
    val txPowerDbm: Int = 20,
)

/**
 * Complete placement recommendation
 *
 * @property currentCoveragePercentage Current coverage level
 * @property targetCoveragePercentage Desired coverage level
 * @property deadZoneCount Number of dead zones detected
 * @property newApPlacements Suggested new AP locations
 * @property repositioningRecommendations Suggestions to move existing APs
 * @property powerAdjustments Power level adjustments
 * @property channelAssignments Channel assignments
 * @property overallScore Placement quality score (0-100)
 * @property estimatedCostLevel Implementation cost estimate
 */
data class PlacementRecommendation(
    val currentCoveragePercentage: Double,
    val targetCoveragePercentage: Double,
    val deadZoneCount: Int,
    val newApPlacements: List<ApPlacementSuggestion>,
    val repositioningRecommendations: List<RepositioningSuggestion>,
    val powerAdjustments: List<PowerAdjustment>,
    val channelAssignments: List<ChannelAssignment>,
    val overallScore: Int,
    val estimatedCostLevel: CostLevel,
) {
    /**
     * Total number of recommendations
     */
    val totalRecommendations: Int
        get() =
            newApPlacements.size +
                repositioningRecommendations.size +
                powerAdjustments.size +
                channelAssignments.size

    /**
     * Whether deployment meets target coverage
     */
    val meetsTarget: Boolean
        get() = currentCoveragePercentage >= targetCoveragePercentage

    /**
     * High priority recommendations only
     */
    val priorityRecommendations: List<Any>
        get() = (
            newApPlacements.filter { it.priority >= 4 } +
                repositioningRecommendations.filter { it.priority >= 4 }
        )

    /**
     * Human-readable summary
     */
    val summary: String
        get() =
            buildString {
                append("Coverage: ${currentCoveragePercentage.toInt()}%")
                append(", Score: $overallScore/100")
                if (!meetsTarget) {
                    append(", ${newApPlacements.size} new APs recommended")
                }
                if (deadZoneCount > 0) {
                    append(", $deadZoneCount dead zones")
                }
            }
}

/**
 * New AP placement suggestion
 */
data class ApPlacementSuggestion(
    val location: SurveyLocation,
    val priority: Int,
    val reasoning: String,
    val expectedImpact: PlacementImpact,
    val estimatedCoverage: Double,
)

/**
 * AP repositioning suggestion
 */
data class RepositioningSuggestion(
    val currentApId: String,
    val currentLocation: SurveyLocation,
    val suggestedLocation: SurveyLocation,
    val distanceMeters: Double,
    val priority: Int,
    val reasoning: String,
    val expectedImpact: PlacementImpact,
)

/**
 * Power adjustment suggestion
 */
data class PowerAdjustment(
    val apId: String,
    val location: SurveyLocation,
    val currentPowerDbm: Int,
    val suggestedPowerDbm: Int,
    val reasoning: String,
    val expectedImpact: PlacementImpact,
) {
    val powerChangeDbm: Int
        get() = suggestedPowerDbm - currentPowerDbm
}

/**
 * Channel assignment suggestion
 */
data class ChannelAssignment(
    val apId: String,
    val location: SurveyLocation,
    val currentChannel: Int?,
    val suggestedChannel: Int,
    val reasoning: String,
    val expectedImpact: PlacementImpact,
)

/**
 * Placement impact levels
 */
enum class PlacementImpact(
    val displayName: String,
) {
    /** Critical impact - addresses no coverage */
    CRITICAL("Critical"),

    /** High impact - significant improvement */
    HIGH("High"),

    /** Medium impact - moderate improvement */
    MEDIUM("Medium"),

    /** Low impact - minor optimization */
    LOW("Low"),
}

/**
 * Implementation cost levels
 */
enum class CostLevel(
    val displayName: String,
) {
    /** High cost (3+ new APs or major work) */
    HIGH("High"),

    /** Medium cost (1-2 new APs or moderate work) */
    MEDIUM("Medium"),

    /** Low cost (minor repositioning) */
    LOW("Low"),

    /** Minimal cost (config changes only) */
    MINIMAL("Minimal"),
}
