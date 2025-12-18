package io.lamco.netkit.optimizer.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for CoverageGoals
 *
 * Tests:
 * - Validation logic for all goals
 * - Factory methods for different deployment types
 * - Computed properties
 * - Quality tiers and tolerance levels
 */
class CoverageGoalsTest {

    // ========================================
    // Constructor Validation Tests
    // ========================================

    @Test
    fun `create goals with valid parameters succeeds`() {
        val goals = CoverageGoals(
            minRssi = -70,
            targetRssi = -60,
            maxInterference = 0.3,
            coverageArea = 100.0,
            minOverlapPercent = 10,
            maxOverlapPercent = 40
        )

        assertEquals(-70, goals.minRssi)
        assertEquals(-60, goals.targetRssi)
        assertEquals(0.3, goals.maxInterference)
        assertEquals(100.0, goals.coverageArea)
    }

    @Test
    fun `create goals with minRssi too low throws exception`() {
        assertThrows<IllegalArgumentException> {
            CoverageGoals(minRssi = -101)
        }
    }

    @Test
    fun `create goals with minRssi too high throws exception`() {
        assertThrows<IllegalArgumentException> {
            CoverageGoals(minRssi = -39)
        }
    }

    @Test
    fun `create goals with targetRssi too low throws exception`() {
        assertThrows<IllegalArgumentException> {
            CoverageGoals(targetRssi = -101)
        }
    }

    @Test
    fun `create goals with targetRssi too high throws exception`() {
        assertThrows<IllegalArgumentException> {
            CoverageGoals(targetRssi = -39)
        }
    }

    @Test
    fun `create goals with targetRssi less than minRssi throws exception`() {
        assertThrows<IllegalArgumentException> {
            CoverageGoals(
                minRssi = -60,
                targetRssi = -70
            )
        }
    }

    @Test
    fun `create goals with maxInterference negative throws exception`() {
        assertThrows<IllegalArgumentException> {
            CoverageGoals(maxInterference = -0.1)
        }
    }

    @Test
    fun `create goals with maxInterference above 1 throws exception`() {
        assertThrows<IllegalArgumentException> {
            CoverageGoals(maxInterference = 1.1)
        }
    }

    @Test
    fun `create goals with negative coverageArea throws exception`() {
        assertThrows<IllegalArgumentException> {
            CoverageGoals(coverageArea = -100.0)
        }
    }

    @Test
    fun `create goals with zero coverageArea throws exception`() {
        assertThrows<IllegalArgumentException> {
            CoverageGoals(coverageArea = 0.0)
        }
    }

    @Test
    fun `create goals with null coverageArea succeeds`() {
        val goals = CoverageGoals(coverageArea = null)
        assertNull(goals.coverageArea)
        assertFalse(goals.hasCoverageConstraint)
    }

    @Test
    fun `create goals with minOverlapPercent negative throws exception`() {
        assertThrows<IllegalArgumentException> {
            CoverageGoals(minOverlapPercent = -1)
        }
    }

    @Test
    fun `create goals with minOverlapPercent above 100 throws exception`() {
        assertThrows<IllegalArgumentException> {
            CoverageGoals(minOverlapPercent = 101)
        }
    }

    @Test
    fun `create goals with maxOverlapPercent above 100 throws exception`() {
        assertThrows<IllegalArgumentException> {
            CoverageGoals(maxOverlapPercent = 101)
        }
    }

    @Test
    fun `create goals with maxOverlapPercent less than minOverlapPercent throws exception`() {
        assertThrows<IllegalArgumentException> {
            CoverageGoals(
                minOverlapPercent = 40,
                maxOverlapPercent = 30
            )
        }
    }

    // ========================================
    // Computed Property Tests
    // ========================================

    @Test
    fun `rssiMargin calculates correctly`() {
        val goals = CoverageGoals(
            minRssi = -70,
            targetRssi = -60
        )

        assertEquals(10, goals.rssiMargin)
    }

    @Test
    fun `rssiMargin is zero when min equals target`() {
        val goals = CoverageGoals(
            minRssi = -60,
            targetRssi = -60
        )

        assertEquals(0, goals.rssiMargin)
    }

    @Test
    fun `hasCoverageConstraint is true when area specified`() {
        val goals = CoverageGoals(coverageArea = 100.0)
        assertTrue(goals.hasCoverageConstraint)
    }

    @Test
    fun `hasCoverageConstraint is false when area is null`() {
        val goals = CoverageGoals(coverageArea = null)
        assertFalse(goals.hasCoverageConstraint)
    }

    @Test
    fun `overlapRange returns correct range`() {
        val goals = CoverageGoals(
            minOverlapPercent = 10,
            maxOverlapPercent = 40
        )

        assertEquals(10..40, goals.overlapRange)
    }

    @Test
    fun `hasStrictOverlapRequirement is true for narrow range`() {
        val goals = CoverageGoals(
            minOverlapPercent = 20,
            maxOverlapPercent = 30  // 10% range
        )

        assertTrue(goals.hasStrictOverlapRequirement)
    }

    @Test
    fun `hasStrictOverlapRequirement is false for wide range`() {
        val goals = CoverageGoals(
            minOverlapPercent = 10,
            maxOverlapPercent = 50  // 40% range
        )

        assertFalse(goals.hasStrictOverlapRequirement)
    }

    // ========================================
    // Quality Tier Tests
    // ========================================

    @Test
    fun `qualityTier is PREMIUM for targetRssi above -50`() {
        val goals = CoverageGoals(targetRssi = -50)
        assertEquals(CoverageQualityTier.PREMIUM, goals.qualityTier)
    }

    @Test
    fun `qualityTier is STANDARD for targetRssi -60`() {
        val goals = CoverageGoals(targetRssi = -60)
        assertEquals(CoverageQualityTier.STANDARD, goals.qualityTier)
    }

    @Test
    fun `qualityTier is BASIC for targetRssi -70`() {
        val goals = CoverageGoals(targetRssi = -70)
        assertEquals(CoverageQualityTier.BASIC, goals.qualityTier)
    }

    @Test
    fun `qualityTier is MINIMAL for targetRssi below -70`() {
        val goals = CoverageGoals(minRssi = -80, targetRssi = -75)
        assertEquals(CoverageQualityTier.MINIMAL, goals.qualityTier)
    }

    // ========================================
    // Interference Tolerance Tests
    // ========================================

    @Test
    fun `interferenceTolerance is STRICT for low maxInterference`() {
        val goals = CoverageGoals(maxInterference = 0.15)
        assertEquals(InterferenceTolerance.STRICT, goals.interferenceTolerance)
    }

    @Test
    fun `interferenceTolerance is MODERATE for medium maxInterference`() {
        val goals = CoverageGoals(maxInterference = 0.3)
        assertEquals(InterferenceTolerance.MODERATE, goals.interferenceTolerance)
    }

    @Test
    fun `interferenceTolerance is PERMISSIVE for high maxInterference`() {
        val goals = CoverageGoals(maxInterference = 0.6)
        assertEquals(InterferenceTolerance.PERMISSIVE, goals.interferenceTolerance)
    }

    // ========================================
    // Summary Tests
    // ========================================

    @Test
    fun `summary contains key information`() {
        val goals = CoverageGoals(
            minRssi = -70,
            targetRssi = -60,
            maxInterference = 0.3,
            coverageArea = 100.0,
            minOverlapPercent = 10,
            maxOverlapPercent = 40,
            prioritizeRoaming = true
        )

        val summary = goals.summary
        assertTrue(summary.contains("-70"))
        assertTrue(summary.contains("-60"))
        assertTrue(summary.contains("30%"))
        assertTrue(summary.contains("100"))
        assertTrue(summary.contains("10-40%"))
        assertTrue(summary.contains("roaming"))
    }

    @Test
    fun `summary without roaming priority omits roaming text`() {
        val goals = CoverageGoals(prioritizeRoaming = false)
        assertFalse(goals.summary.contains("roaming"))
    }

    // ========================================
    // Factory Method Tests - Residential
    // ========================================

    @Test
    fun `residential factory creates appropriate goals`() {
        val goals = CoverageGoals.residential()

        assertEquals(-70, goals.minRssi)
        assertEquals(-60, goals.targetRssi)
        assertEquals(0.3, goals.maxInterference)
        assertNull(goals.coverageArea)
        assertEquals(15, goals.minOverlapPercent)
        assertEquals(35, goals.maxOverlapPercent)
        assertTrue(goals.prioritizeRoaming)
    }

    @Test
    fun `residential has moderate interference tolerance`() {
        val goals = CoverageGoals.residential()
        assertEquals(InterferenceTolerance.MODERATE, goals.interferenceTolerance)
    }

    @Test
    fun `residential has standard quality tier`() {
        val goals = CoverageGoals.residential()
        assertEquals(CoverageQualityTier.STANDARD, goals.qualityTier)
    }

    // ========================================
    // Factory Method Tests - Enterprise
    // ========================================

    @Test
    fun `enterprise factory creates appropriate goals`() {
        val goals = CoverageGoals.enterprise()

        assertEquals(-67, goals.minRssi)
        assertEquals(-50, goals.targetRssi)
        assertEquals(0.2, goals.maxInterference)
        assertNull(goals.coverageArea)
        assertEquals(10, goals.minOverlapPercent)
        assertEquals(25, goals.maxOverlapPercent)
        assertFalse(goals.prioritizeRoaming)
    }

    @Test
    fun `enterprise has strict interference tolerance`() {
        val goals = CoverageGoals.enterprise()
        assertEquals(InterferenceTolerance.STRICT, goals.interferenceTolerance)
    }

    @Test
    fun `enterprise has premium quality tier`() {
        val goals = CoverageGoals.enterprise()
        assertEquals(CoverageQualityTier.PREMIUM, goals.qualityTier)
    }

    @Test
    fun `enterprise has strict overlap requirement`() {
        val goals = CoverageGoals.enterprise()
        assertTrue(goals.hasStrictOverlapRequirement)
    }

    // ========================================
    // Factory Method Tests - High Density
    // ========================================

    @Test
    fun `highDensity factory creates appropriate goals`() {
        val goals = CoverageGoals.highDensity()

        assertEquals(-65, goals.minRssi)
        assertEquals(-50, goals.targetRssi)
        assertEquals(0.15, goals.maxInterference)
        assertEquals(5, goals.minOverlapPercent)
        assertEquals(20, goals.maxOverlapPercent)
        assertFalse(goals.prioritizeRoaming)
    }

    @Test
    fun `highDensity has strict interference tolerance`() {
        val goals = CoverageGoals.highDensity()
        assertEquals(InterferenceTolerance.STRICT, goals.interferenceTolerance)
    }

    @Test
    fun `highDensity has premium quality tier`() {
        val goals = CoverageGoals.highDensity()
        assertEquals(CoverageQualityTier.PREMIUM, goals.qualityTier)
    }

    @Test
    fun `highDensity has minimal overlap for capacity`() {
        val goals = CoverageGoals.highDensity()
        assertTrue(goals.minOverlapPercent <= 10)
        assertTrue(goals.maxOverlapPercent <= 25)
    }

    // ========================================
    // Factory Method Tests - Outdoor
    // ========================================

    @Test
    fun `outdoor factory creates appropriate goals`() {
        val goals = CoverageGoals.outdoor()

        assertEquals(-75, goals.minRssi)
        assertEquals(-65, goals.targetRssi)
        assertEquals(0.5, goals.maxInterference)
        assertEquals(20, goals.minOverlapPercent)
        assertEquals(50, goals.maxOverlapPercent)
        assertTrue(goals.prioritizeRoaming)
    }

    @Test
    fun `outdoor has permissive interference tolerance`() {
        val goals = CoverageGoals.outdoor()
        assertEquals(InterferenceTolerance.PERMISSIVE, goals.interferenceTolerance)
    }

    @Test
    fun `outdoor has basic quality tier`() {
        val goals = CoverageGoals.outdoor()
        assertEquals(CoverageQualityTier.BASIC, goals.qualityTier)
    }

    @Test
    fun `outdoor has high overlap for mobility`() {
        val goals = CoverageGoals.outdoor()
        assertTrue(goals.maxOverlapPercent >= 40)
    }

    // ========================================
    // Enum Description Tests
    // ========================================

    @Test
    fun `CoverageQualityTier has descriptions`() {
        assertNotNull(CoverageQualityTier.PREMIUM.displayName)
        assertNotNull(CoverageQualityTier.PREMIUM.description)
        assertTrue(CoverageQualityTier.PREMIUM.description.contains("Excellent"))
    }

    @Test
    fun `InterferenceTolerance has descriptions`() {
        assertNotNull(InterferenceTolerance.STRICT.displayName)
        assertNotNull(InterferenceTolerance.STRICT.description)
        assertTrue(InterferenceTolerance.STRICT.description.contains("capacity"))
    }

    // ========================================
    // Edge Case Tests
    // ========================================

    @Test
    fun `goals with identical minRssi and targetRssi succeeds`() {
        val goals = CoverageGoals(
            minRssi = -60,
            targetRssi = -60
        )

        assertEquals(0, goals.rssiMargin)
    }

    @Test
    fun `goals with maxInterference zero succeeds`() {
        val goals = CoverageGoals(maxInterference = 0.0)
        assertEquals(InterferenceTolerance.STRICT, goals.interferenceTolerance)
    }

    @Test
    fun `goals with maxInterference 1_0 succeeds`() {
        val goals = CoverageGoals(maxInterference = 1.0)
        assertEquals(InterferenceTolerance.PERMISSIVE, goals.interferenceTolerance)
    }

    @Test
    fun `goals with 100% overlap range succeeds`() {
        val goals = CoverageGoals(
            minOverlapPercent = 0,
            maxOverlapPercent = 100
        )

        assertEquals(0..100, goals.overlapRange)
        assertFalse(goals.hasStrictOverlapRequirement)
    }

    @Test
    fun `goals with very small coverageArea succeeds`() {
        val goals = CoverageGoals(coverageArea = 0.1)
        assertEquals(0.1, goals.coverageArea)
        assertTrue(goals.hasCoverageConstraint)
    }

    @Test
    fun `goals with very large coverageArea succeeds`() {
        val goals = CoverageGoals(coverageArea = 1000000.0)
        assertEquals(1000000.0, goals.coverageArea)
    }
}
