package io.lamco.netkit.advisor.troubleshooting

/**
 * Solution recommender for network issues
 *
 * Provides fix suggestions and implementation steps for identified network problems.
 * Uses expert knowledge database (NOT machine learning).
 *
 * Features:
 * - **Difficulty-aware**: Solutions ranked by implementation difficulty
 * - **Impact-based**: Prioritizes high-impact solutions
 * - **Step-by-step**: Detailed implementation instructions
 * - **Prerequisite checking**: Identifies what's needed before applying fix
 *
 * Example Usage:
 * ```kotlin
 * val recommender = SolutionRecommender()
 *
 * val solution = recommender.recommendSolution(
 *     issue = NetworkIssue.WEAK_SIGNAL,
 *     difficulty = Difficulty.EASY
 * )
 *
 * println(solution.solution)
 * solution.steps.forEach { println("- $it") }
 * ```
 */
class SolutionRecommender {
    /**
     * Recommend solution for network issue
     *
     * @param issue Network issue to resolve
     * @param maxDifficulty Maximum acceptable solution difficulty
     * @return Solution recommendation
     */
    fun recommendSolution(
        issue: NetworkIssue,
        maxDifficulty: Difficulty = Difficulty.HARD,
    ): SolutionRecommendation? {
        val solutions = SOLUTION_DATABASE[issue] ?: return null

        // Filter by difficulty and return highest impact
        return solutions
            .filter { it.difficulty.ordinal <= maxDifficulty.ordinal }
            .maxByOrNull { it.impact.ordinal }
    }

    /**
     * Get all solutions for an issue
     *
     * @param issue Network issue
     * @return List of all possible solutions, ordered by impact
     */
    fun getAllSolutions(issue: NetworkIssue): List<SolutionRecommendation> =
        SOLUTION_DATABASE[issue]?.sortedByDescending { it.impact.ordinal } ?: emptyList()

    /**
     * Recommend multiple solutions for multiple issues
     *
     * @param issues List of network issues
     * @param maxDifficulty Maximum acceptable difficulty
     * @return Map of issue to recommended solution
     */
    fun recommendSolutions(
        issues: List<NetworkIssue>,
        maxDifficulty: Difficulty = Difficulty.HARD,
    ): Map<NetworkIssue, SolutionRecommendation> =
        issues
            .mapNotNull { issue ->
                recommendSolution(issue, maxDifficulty)?.let { issue to it }
            }.toMap()

    /**
     * Get quick fixes (easy difficulty, high impact)
     *
     * @param issue Network issue
     * @return Quick fix solution if available
     */
    fun getQuickFix(issue: NetworkIssue): SolutionRecommendation? {
        val solutions = SOLUTION_DATABASE[issue] ?: return null

        return solutions
            .filter { it.difficulty == Difficulty.EASY }
            .maxByOrNull { it.impact.ordinal }
    }

    companion object {
        /**
         * Solution database for all network issues
         */
        private val SOLUTION_DATABASE =
            mapOf(
                NetworkIssue.WEAK_SIGNAL to
                    listOf(
                        SolutionRecommendation(
                            issue = NetworkIssue.WEAK_SIGNAL,
                            solution = "Move closer to WiFi access point",
                            steps =
                                listOf(
                                    "Check current signal strength (dBm)",
                                    "Move device closer to AP in small increments",
                                    "Test signal strength after each move",
                                    "Find location with signal > -65dBm for best performance",
                                ),
                            difficulty = Difficulty.EASY,
                            impact = Impact.MEDIUM,
                        ),
                        SolutionRecommendation(
                            issue = NetworkIssue.WEAK_SIGNAL,
                            solution = "Increase AP transmit power",
                            steps =
                                listOf(
                                    "Log into AP admin interface",
                                    "Navigate to wireless settings",
                                    "Increase transmit power to 75-100%",
                                    "Save settings and test signal at problem location",
                                ),
                            difficulty = Difficulty.MEDIUM,
                            impact = Impact.HIGH,
                            prerequisites = listOf("Admin access to AP", "Adjustable transmit power support"),
                        ),
                        SolutionRecommendation(
                            issue = NetworkIssue.WEAK_SIGNAL,
                            solution = "Add additional access point for coverage",
                            steps =
                                listOf(
                                    "Perform site survey to identify coverage gaps",
                                    "Purchase compatible AP",
                                    "Install AP in area with weak coverage",
                                    "Configure with same SSID for seamless roaming",
                                    "Verify coverage improvement",
                                ),
                            difficulty = Difficulty.HARD,
                            impact = Impact.HIGH,
                            prerequisites = listOf("Additional AP hardware", "Network cabling or PoE", "Configuration expertise"),
                        ),
                    ),
                NetworkIssue.CHANNEL_CONGESTION to
                    listOf(
                        SolutionRecommendation(
                            issue = NetworkIssue.CHANNEL_CONGESTION,
                            solution = "Switch to less congested WiFi channel",
                            steps =
                                listOf(
                                    "Use WiFi analyzer app to scan channels",
                                    "Identify channel with lowest utilization",
                                    "For 2.4GHz: Use channel 1, 6, or 11 only",
                                    "For 5GHz: Prefer non-DFS channels (36-48, 149-165)",
                                    "Change AP to selected channel",
                                    "Test performance improvement",
                                ),
                            difficulty = Difficulty.MEDIUM,
                            impact = Impact.HIGH,
                        ),
                        SolutionRecommendation(
                            issue = NetworkIssue.CHANNEL_CONGESTION,
                            solution = "Switch from 2.4GHz to 5GHz band",
                            steps =
                                listOf(
                                    "Verify devices support 5GHz",
                                    "Enable 5GHz network on AP",
                                    "Connect devices to 5GHz SSID",
                                    "Disable or reduce power on 2.4GHz",
                                    "Verify improved performance",
                                ),
                            difficulty = Difficulty.EASY,
                            impact = Impact.HIGH,
                            prerequisites = listOf("Dual-band AP", "5GHz capable devices"),
                        ),
                    ),
                NetworkIssue.AP_OVERLOADED to
                    listOf(
                        SolutionRecommendation(
                            issue = NetworkIssue.AP_OVERLOADED,
                            solution = "Enable client load balancing",
                            steps =
                                listOf(
                                    "Access AP configuration",
                                    "Enable load balancing feature",
                                    "Set client limit per AP (e.g., 30 clients)",
                                    "Enable band steering to distribute clients",
                                    "Monitor client distribution",
                                ),
                            difficulty = Difficulty.MEDIUM,
                            impact = Impact.MEDIUM,
                            prerequisites = listOf("Multiple APs", "Load balancing support"),
                        ),
                        SolutionRecommendation(
                            issue = NetworkIssue.AP_OVERLOADED,
                            solution = "Add additional access points",
                            steps =
                                listOf(
                                    "Calculate required APs (1 per 20-30 clients)",
                                    "Purchase additional APs",
                                    "Install in strategic locations",
                                    "Configure for load distribution",
                                    "Enable 802.11k/v for better roaming",
                                ),
                            difficulty = Difficulty.HARD,
                            impact = Impact.HIGH,
                            prerequisites = listOf("Additional hardware", "Network infrastructure"),
                        ),
                    ),
                NetworkIssue.INTERFERENCE to
                    listOf(
                        SolutionRecommendation(
                            issue = NetworkIssue.INTERFERENCE,
                            solution = "Change to non-overlapping channel",
                            steps =
                                listOf(
                                    "Scan for interference sources",
                                    "For 2.4GHz: Switch to channel 1, 6, or 11",
                                    "For 5GHz: Use DFS channels for more options",
                                    "Avoid channels used by neighbors",
                                    "Test for improvement",
                                ),
                            difficulty = Difficulty.EASY,
                            impact = Impact.HIGH,
                        ),
                        SolutionRecommendation(
                            issue = NetworkIssue.INTERFERENCE,
                            solution = "Identify and eliminate interference source",
                            steps =
                                listOf(
                                    "Note when interference occurs (time pattern)",
                                    "Check for microwave ovens, cordless phones, Bluetooth",
                                    "Temporarily disable suspected devices",
                                    "Test network performance",
                                    "Relocate or replace interfering devices",
                                ),
                            difficulty = Difficulty.MEDIUM,
                            impact = Impact.MEDIUM,
                        ),
                    ),
                NetworkIssue.WRONG_PASSWORD to
                    listOf(
                        SolutionRecommendation(
                            issue = NetworkIssue.WRONG_PASSWORD,
                            solution = "Verify and update WiFi password",
                            steps =
                                listOf(
                                    "Check AP for correct password",
                                    "Forget network on device",
                                    "Reconnect with correct password",
                                    "If password unknown, reset AP to factory defaults",
                                ),
                            difficulty = Difficulty.EASY,
                            impact = Impact.HIGH,
                        ),
                    ),
                NetworkIssue.OUTDATED_FIRMWARE to
                    listOf(
                        SolutionRecommendation(
                            issue = NetworkIssue.OUTDATED_FIRMWARE,
                            solution = "Update AP firmware",
                            steps =
                                listOf(
                                    "Check current firmware version",
                                    "Visit manufacturer website for latest firmware",
                                    "Download firmware file",
                                    "Backup AP configuration",
                                    "Upload firmware via AP admin interface",
                                    "Allow AP to reboot (do not interrupt)",
                                    "Verify update completed successfully",
                                ),
                            difficulty = Difficulty.MEDIUM,
                            impact = Impact.MEDIUM,
                            prerequisites = listOf("Admin access", "Stable power during update"),
                        ),
                    ),
                NetworkIssue.INSUFFICIENT_COVERAGE to
                    listOf(
                        SolutionRecommendation(
                            issue = NetworkIssue.INSUFFICIENT_COVERAGE,
                            solution = "Deploy additional access points",
                            steps =
                                listOf(
                                    "Conduct site survey to map coverage",
                                    "Identify dead zones and weak areas",
                                    "Calculate required AP count and placement",
                                    "Install APs in optimal locations",
                                    "Configure mesh or same-SSID roaming",
                                    "Verify complete coverage",
                                ),
                            difficulty = Difficulty.HARD,
                            impact = Impact.HIGH,
                            prerequisites = listOf("Multiple APs", "Site survey tools", "Installation expertise"),
                        ),
                    ),
                NetworkIssue.POOR_AP_PLACEMENT to
                    listOf(
                        SolutionRecommendation(
                            issue = NetworkIssue.POOR_AP_PLACEMENT,
                            solution = "Relocate AP to optimal position",
                            steps =
                                listOf(
                                    "Move AP to central location",
                                    "Place AP high (ceiling or wall-mounted)",
                                    "Keep away from metal objects and walls",
                                    "Orient antennas properly (vertical for horizontal coverage)",
                                    "Test coverage at key locations",
                                ),
                            difficulty = Difficulty.MEDIUM,
                            impact = Impact.HIGH,
                        ),
                    ),
                NetworkIssue.CLIENT_ROAMING_FAILURE to
                    listOf(
                        SolutionRecommendation(
                            issue = NetworkIssue.CLIENT_ROAMING_FAILURE,
                            solution = "Enable fast roaming (802.11r)",
                            steps =
                                listOf(
                                    "Access AP configuration",
                                    "Enable 802.11r fast roaming",
                                    "Enable 802.11k neighbor reports",
                                    "Enable 802.11v BSS transition management",
                                    "Set aggressive minimum RSSI (-70 to -75dBm)",
                                    "Test roaming between APs",
                                ),
                            difficulty = Difficulty.MEDIUM,
                            impact = Impact.HIGH,
                            prerequisites = listOf("802.11r support on AP and clients", "Multiple APs"),
                        ),
                    ),
                NetworkIssue.ISP_ISSUE to
                    listOf(
                        SolutionRecommendation(
                            issue = NetworkIssue.ISP_ISSUE,
                            solution = "Contact ISP for support",
                            steps =
                                listOf(
                                    "Test speed directly from modem (bypass WiFi)",
                                    "Document speed test results",
                                    "Check for service outages in your area",
                                    "Contact ISP support with test results",
                                    "Request modem/line diagnostics",
                                ),
                            difficulty = Difficulty.EASY,
                            impact = Impact.MEDIUM,
                        ),
                        SolutionRecommendation(
                            issue = NetworkIssue.ISP_ISSUE,
                            solution = "Restart modem and router",
                            steps =
                                listOf(
                                    "Power off modem and router",
                                    "Wait 30 seconds",
                                    "Power on modem, wait for full boot",
                                    "Power on router, wait for full boot",
                                    "Test connectivity",
                                ),
                            difficulty = Difficulty.EASY,
                            impact = Impact.LOW,
                        ),
                    ),
            )
    }
}
