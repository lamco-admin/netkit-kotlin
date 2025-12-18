package io.lamco.netkit.model.security

import io.lamco.netkit.model.network.WiFiBand

/**
 * Overall security rating for a WiFi network (0-100 scale)
 *
 * Scoring breakdown:
 * - Base security type: 50 points max
 *   - WPA3: 50 points
 *   - WPA2-Enterprise: 38 points
 *   - WPA2-Personal: 35 points
 *   - Enhanced Open: 25 points
 *   - WPA: 15 points
 *   - WEP: 5 points
 *   - Open: 0 points
 *
 * - Security features: 25 points max
 *   - SAE: +8 points
 *   - PMF Required: +8 points
 *   - H2E: +5 points
 *   - Transition Disable: +4 points
 *
 * - Band security: 15 points max
 *   - 6 GHz: +15 points (mandatory WPA3)
 *   - 5 GHz: +8 points
 *   - 2.4 GHz: +3 points
 *
 * - Advanced features: 10 points max
 *   - WiFi 6E: +6 points
 *   - WiFi 7 MLO: +4 points
 *
 * - Penalties:
 *   - Transition mode: -10 points
 *
 * @property grade Letter grade (A+ to F)
 * @property score Numerical score (0-100)
 * @property category Human-readable category
 */
data class SecurityRating(
    val grade: Grade,
    val score: Int, // 0-100
    val category: String,
) {
    /**
     * Letter grade for security rating
     *
     * @property displayName Single character or short string ("A+", "A", "B", etc.)
     * @property colorHex Hex color code for UI display (without # prefix)
     * @property description Human-readable description
     */
    enum class Grade(
        val displayName: String,
        val colorHex: String, // Hex without '#' prefix for platform independence
        val description: String,
    ) {
        A_PLUS(
            "A+",
            "4CAF50", // Green
            "Excellent - Maximum security with latest standards",
        ),
        A(
            "A",
            "8BC34A", // Light Green
            "Very Good - Strong security with modern protocols",
        ),
        B(
            "B",
            "FFEB3B", // Yellow
            "Good - Adequate security, consider upgrading",
        ),
        C(
            "C",
            "FF9800", // Orange
            "Fair - Outdated security, upgrade recommended",
        ),
        D(
            "D",
            "FF5722", // Deep Orange
            "Poor - Weak security, upgrade urgently",
        ),
        F(
            "F",
            "F44336", // Red
            "Critical - Severely compromised security",
        ),
        UNKNOWN(
            "?",
            "9E9E9E", // Gray
            "Unknown security status",
        ),
        ;

        companion object {
            /**
             * Convert numerical score to letter grade
             *
             * @param score Security score (0-100)
             * @return Corresponding letter grade
             */
            fun fromScore(score: Int): Grade =
                when (score) {
                    in 95..100 -> A_PLUS
                    in 85..94 -> A
                    in 70..84 -> B
                    in 50..69 -> C
                    in 30..49 -> D
                    in 0..29 -> F
                    else -> UNKNOWN
                }
        }
    }

    companion object {
        /**
         * Calculate security rating from network properties
         *
         * @param securityTypes Detected security types (can be multiple in transition mode)
         * @param securityFeatures Detected advanced security features
         * @param band WiFi band (2.4/5/6 GHz)
         * @param isTransitionMode Whether network runs WPA2+WPA3 dual mode
         * @return Calculated security rating
         */
        fun calculate(
            securityTypes: List<SecurityType>,
            securityFeatures: List<SecurityFeature>,
            band: WiFiBand,
            isTransitionMode: Boolean,
        ): SecurityRating {
            val score =
                scoreSecurityType(securityTypes) +
                    scoreSecurityFeatures(securityFeatures) +
                    scoreBandSecurity(band) +
                    applyTransitionModePenalty(isTransitionMode, securityTypes) +
                    scoreAdvancedFeatures(securityFeatures)

            val finalScore = score.coerceIn(0, 100)
            val grade = Grade.fromScore(finalScore)
            val category = determineCategory(securityTypes, isTransitionMode)

            return SecurityRating(grade, finalScore, category)
        }

        private fun scoreSecurityType(securityTypes: List<SecurityType>): Int {
            val bestSecurity = securityTypes.maxByOrNull { it.securityLevel }
            return when (bestSecurity) {
                SecurityType.WPA3_PERSONAL,
                SecurityType.WPA3_ENTERPRISE_192,
                SecurityType.WPA3_ENTERPRISE,
                -> 50
                SecurityType.WPA2_ENTERPRISE -> 38
                SecurityType.WPA2_PERSONAL -> 35
                SecurityType.ENHANCED_OPEN -> 25
                SecurityType.WPA_PERSONAL -> 15
                SecurityType.WEP -> 5
                SecurityType.OPEN -> 0
                else -> 0
            }
        }

        private fun scoreSecurityFeatures(securityFeatures: List<SecurityFeature>): Int {
            var score = 0
            if (SecurityFeature.SAE_AUTHENTICATION in securityFeatures) score += 8
            if (SecurityFeature.PMF_REQUIRED in securityFeatures) score += 8
            if (SecurityFeature.H2E_SUPPORT in securityFeatures) score += 5
            if (SecurityFeature.TRANSITION_DISABLE in securityFeatures) score += 4
            return score
        }

        /**
         * Score band security (15 points max).
         *
         * Higher frequencies have shorter range, reducing attack surface.
         * 6 GHz also mandates WPA3.
         */
        private fun scoreBandSecurity(band: WiFiBand): Int =
            when (band) {
                WiFiBand.BAND_6GHZ -> 15 // Mandatory WPA3, shortest range
                WiFiBand.BAND_5GHZ -> 8 // Shorter range than 2.4
                WiFiBand.BAND_2_4GHZ -> 3 // Longest range = larger attack surface
                WiFiBand.UNKNOWN -> 0
            }

        /**
         * Apply transition mode penalty (-10 points).
         *
         * CRITICAL: Same password for WPA2 and WPA3 means security = weakest link.
         * If attacker captures WPA2 handshake from legacy device, can crack offline.
         */
        private fun applyTransitionModePenalty(
            isTransitionMode: Boolean,
            securityTypes: List<SecurityType>,
        ): Int = if (isTransitionMode && SecurityType.WPA3_PERSONAL in securityTypes) -10 else 0

        private fun scoreAdvancedFeatures(securityFeatures: List<SecurityFeature>): Int {
            var score = 0
            if (SecurityFeature.WIFI_6E in securityFeatures) score += 6
            if (SecurityFeature.WIFI_7_MLO in securityFeatures) score += 4
            return score
        }

        private fun determineCategory(
            securityTypes: List<SecurityType>,
            isTransitionMode: Boolean,
        ): String =
            when {
                SecurityType.WPA3_PERSONAL in securityTypes && !isTransitionMode -> "Excellent Security"
                SecurityType.WPA3_PERSONAL in securityTypes && isTransitionMode -> "Good Security (Mixed Mode)"
                SecurityType.WPA2_PERSONAL in securityTypes -> "Standard Security"
                SecurityType.WPA_PERSONAL in securityTypes -> "Legacy Security"
                SecurityType.OPEN in securityTypes -> "No Security"
                SecurityType.WEP in securityTypes -> "Broken Security"
                else -> "Unknown Security"
            }

        /**
         * Create SecurityRating directly from score
         * Useful when score is already calculated
         *
         * @param score Security score (0-100)
         * @param category Optional category override
         * @return SecurityRating with appropriate grade
         */
        fun fromScore(
            score: Int,
            category: String? = null,
        ): SecurityRating {
            val grade = Grade.fromScore(score)
            val cat =
                category ?: when (score) {
                    in 85..100 -> "Excellent Security"
                    in 70..84 -> "Good Security"
                    in 50..69 -> "Fair Security"
                    in 30..49 -> "Weak Security"
                    else -> "Critical Security Risk"
                }
            return SecurityRating(grade, score, cat)
        }
    }
}
