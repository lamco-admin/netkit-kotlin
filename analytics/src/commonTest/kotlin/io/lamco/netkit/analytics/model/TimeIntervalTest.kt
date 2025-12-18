package io.lamco.netkit.analytics.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comprehensive tests for TimeInterval enum
 */
class TimeIntervalTest {
    // ========================================
    // Basic Enum Properties Tests
    // ========================================

    @Test
    fun `HOURLY has correct duration milliseconds`() {
        assertEquals(3600_000L, TimeInterval.HOURLY.durationMillis)
        assertEquals("Hourly", TimeInterval.HOURLY.displayName)
    }

    @Test
    fun `DAILY has correct duration milliseconds`() {
        assertEquals(86400_000L, TimeInterval.DAILY.durationMillis)
        assertEquals("Daily", TimeInterval.DAILY.displayName)
    }

    @Test
    fun `WEEKLY has correct duration milliseconds`() {
        assertEquals(604800_000L, TimeInterval.WEEKLY.durationMillis)
        assertEquals("Weekly", TimeInterval.WEEKLY.displayName)
    }

    @Test
    fun `MONTHLY has correct duration milliseconds`() {
        assertEquals(2592000_000L, TimeInterval.MONTHLY.durationMillis)
        assertEquals("Monthly", TimeInterval.MONTHLY.displayName)
    }

    // ========================================
    // Short-Term vs Long-Term Classification Tests
    // ========================================

    @Test
    fun `HOURLY is classified as short-term`() {
        assertTrue(TimeInterval.HOURLY.isShortTerm)
        assertFalse(TimeInterval.HOURLY.isLongTerm)
    }

    @Test
    fun `DAILY is classified as short-term`() {
        assertTrue(TimeInterval.DAILY.isShortTerm)
        assertFalse(TimeInterval.DAILY.isLongTerm)
    }

    @Test
    fun `WEEKLY is classified as long-term`() {
        assertFalse(TimeInterval.WEEKLY.isShortTerm)
        assertTrue(TimeInterval.WEEKLY.isLongTerm)
    }

    @Test
    fun `MONTHLY is classified as long-term`() {
        assertFalse(TimeInterval.MONTHLY.isShortTerm)
        assertTrue(TimeInterval.MONTHLY.isLongTerm)
    }

    // ========================================
    // Recommended Minimum Sample Size Tests
    // ========================================

    @Test
    fun `HOURLY has appropriate minimum sample size`() {
        assertEquals(10, TimeInterval.HOURLY.recommendedMinSampleSize)
    }

    @Test
    fun `DAILY has appropriate minimum sample size`() {
        assertEquals(24, TimeInterval.DAILY.recommendedMinSampleSize)
    }

    @Test
    fun `WEEKLY has appropriate minimum sample size`() {
        assertEquals(50, TimeInterval.WEEKLY.recommendedMinSampleSize)
    }

    @Test
    fun `MONTHLY has appropriate minimum sample size`() {
        assertEquals(100, TimeInterval.MONTHLY.recommendedMinSampleSize)
    }

    // ========================================
    // fromDuration Tests
    // ========================================

    @Test
    fun `fromDuration returns HOURLY for very short duration`() {
        val duration = TimeInterval.HOURLY.durationMillis
        assertEquals(TimeInterval.HOURLY, TimeInterval.fromDuration(duration))
    }

    @Test
    fun `fromDuration returns HOURLY for 1 day duration`() {
        val duration = TimeInterval.DAILY.durationMillis
        assertEquals(TimeInterval.HOURLY, TimeInterval.fromDuration(duration))
    }

    @Test
    fun `fromDuration returns DAILY for 3 days duration`() {
        val duration = TimeInterval.DAILY.durationMillis * 3
        assertEquals(TimeInterval.DAILY, TimeInterval.fromDuration(duration))
    }

    @Test
    fun `fromDuration returns DAILY for 2 weeks duration`() {
        // 2 weeks < 4 weeks threshold → returns DAILY
        val duration = TimeInterval.WEEKLY.durationMillis * 2
        assertEquals(TimeInterval.DAILY, TimeInterval.fromDuration(duration))
    }

    @Test
    fun `fromDuration returns WEEKLY for 10 weeks duration`() {
        // 10 weeks < 3 months threshold → returns WEEKLY
        val duration = TimeInterval.WEEKLY.durationMillis * 10
        assertEquals(TimeInterval.WEEKLY, TimeInterval.fromDuration(duration))
    }

    @Test
    fun `fromDuration returns MONTHLY for very long duration`() {
        val duration = TimeInterval.MONTHLY.durationMillis * 12
        assertEquals(TimeInterval.MONTHLY, TimeInterval.fromDuration(duration))
    }

    // ========================================
    // suitableIntervalsFor Tests
    // ========================================

    @Test
    fun `suitableIntervalsFor 1 hour returns empty list`() {
        val duration = TimeInterval.HOURLY.durationMillis
        val suitable = TimeInterval.suitableIntervalsFor(duration)
        assertTrue(suitable.isEmpty())
    }

    @Test
    fun `suitableIntervalsFor 3 hours returns HOURLY only`() {
        val duration = TimeInterval.HOURLY.durationMillis * 3
        val suitable = TimeInterval.suitableIntervalsFor(duration)
        assertEquals(listOf(TimeInterval.HOURLY), suitable)
    }

    @Test
    fun `suitableIntervalsFor 3 days returns HOURLY and DAILY`() {
        val duration = TimeInterval.DAILY.durationMillis * 3
        val suitable = TimeInterval.suitableIntervalsFor(duration)
        assertEquals(listOf(TimeInterval.HOURLY, TimeInterval.DAILY), suitable)
    }

    @Test
    fun `suitableIntervalsFor 3 weeks returns HOURLY DAILY WEEKLY`() {
        val duration = TimeInterval.WEEKLY.durationMillis * 3
        val suitable = TimeInterval.suitableIntervalsFor(duration)
        assertEquals(
            listOf(TimeInterval.HOURLY, TimeInterval.DAILY, TimeInterval.WEEKLY),
            suitable,
        )
    }

    @Test
    fun `suitableIntervalsFor 3 months returns all intervals`() {
        val duration = TimeInterval.MONTHLY.durationMillis * 3
        val suitable = TimeInterval.suitableIntervalsFor(duration)
        assertEquals(TimeInterval.entries, suitable)
    }

    // ========================================
    // Edge Cases
    // ========================================

    @Test
    fun `fromDuration handles zero duration gracefully`() {
        assertEquals(TimeInterval.HOURLY, TimeInterval.fromDuration(0))
    }

    @Test
    fun `suitableIntervalsFor zero duration returns empty list`() {
        val suitable = TimeInterval.suitableIntervalsFor(0)
        assertTrue(suitable.isEmpty())
    }

    @Test
    fun `all intervals have positive durations`() {
        TimeInterval.entries.forEach { interval ->
            assertTrue(interval.durationMillis > 0, "$interval should have positive duration")
        }
    }

    @Test
    fun `all intervals have positive sample sizes`() {
        TimeInterval.entries.forEach { interval ->
            assertTrue(
                interval.recommendedMinSampleSize > 0,
                "$interval should have positive sample size",
            )
        }
    }

    @Test
    fun `intervals are ordered from shortest to longest`() {
        val durations = TimeInterval.entries.map { it.durationMillis }
        assertEquals(durations.sorted(), durations)
    }
}

/**
 * Comprehensive tests for SmoothingMethod enum
 */
class SmoothingMethodTest {
    // ========================================
    // Basic Enum Properties Tests
    // ========================================

    @Test
    fun `MOVING_AVERAGE has correct properties`() {
        assertEquals("Moving Average", SmoothingMethod.MOVING_AVERAGE.displayName)
        assertEquals(3, SmoothingMethod.MOVING_AVERAGE.requiresMinimumPoints)
    }

    @Test
    fun `EXPONENTIAL_SMOOTHING has correct properties`() {
        assertEquals("Exponential Smoothing", SmoothingMethod.EXPONENTIAL_SMOOTHING.displayName)
        assertEquals(2, SmoothingMethod.EXPONENTIAL_SMOOTHING.requiresMinimumPoints)
    }

    @Test
    fun `SAVITZKY_GOLAY has correct properties`() {
        assertEquals("Savitzky-Golay", SmoothingMethod.SAVITZKY_GOLAY.displayName)
        assertEquals(5, SmoothingMethod.SAVITZKY_GOLAY.requiresMinimumPoints)
    }

    // ========================================
    // Method Characteristics Tests
    // ========================================

    @Test
    fun `MOVING_AVERAGE is fast`() {
        assertTrue(SmoothingMethod.MOVING_AVERAGE.isFast)
        assertFalse(SmoothingMethod.MOVING_AVERAGE.preservesFeatures)
    }

    @Test
    fun `EXPONENTIAL_SMOOTHING is fast`() {
        assertTrue(SmoothingMethod.EXPONENTIAL_SMOOTHING.isFast)
        assertFalse(SmoothingMethod.EXPONENTIAL_SMOOTHING.preservesFeatures)
    }

    @Test
    fun `SAVITZKY_GOLAY preserves features but is not fast`() {
        assertFalse(SmoothingMethod.SAVITZKY_GOLAY.isFast)
        assertTrue(SmoothingMethod.SAVITZKY_GOLAY.preservesFeatures)
    }

    // ========================================
    // recommend Tests
    // ========================================

    @Test
    fun `recommend returns EXPONENTIAL_SMOOTHING for small dataset`() {
        val method = SmoothingMethod.recommend(dataSize = 3, needsSpeed = false)
        assertEquals(SmoothingMethod.EXPONENTIAL_SMOOTHING, method)
    }

    @Test
    fun `recommend returns MOVING_AVERAGE when speed is priority`() {
        val method = SmoothingMethod.recommend(dataSize = 100, needsSpeed = true)
        assertEquals(SmoothingMethod.MOVING_AVERAGE, method)
    }

    @Test
    fun `recommend returns SAVITZKY_GOLAY for large dataset when speed not priority`() {
        val method = SmoothingMethod.recommend(dataSize = 100, needsSpeed = false)
        assertEquals(SmoothingMethod.SAVITZKY_GOLAY, method)
    }

    @Test
    fun `recommend returns EXPONENTIAL_SMOOTHING for dataset just below SAVITZKY_GOLAY minimum`() {
        val method = SmoothingMethod.recommend(dataSize = 4, needsSpeed = false)
        assertEquals(SmoothingMethod.EXPONENTIAL_SMOOTHING, method)
    }

    @Test
    fun `recommend returns SAVITZKY_GOLAY for dataset at exactly minimum size`() {
        val method = SmoothingMethod.recommend(dataSize = 5, needsSpeed = false)
        assertEquals(SmoothingMethod.SAVITZKY_GOLAY, method)
    }

    // ========================================
    // Edge Cases
    // ========================================

    @Test
    fun `all methods have positive minimum point requirements`() {
        SmoothingMethod.entries.forEach { method ->
            assertTrue(
                method.requiresMinimumPoints > 0,
                "$method should require positive minimum points",
            )
        }
    }

    @Test
    fun `minimum point requirements are ordered logically`() {
        assertTrue(
            SmoothingMethod.EXPONENTIAL_SMOOTHING.requiresMinimumPoints <=
                SmoothingMethod.MOVING_AVERAGE.requiresMinimumPoints,
        )
        assertTrue(
            SmoothingMethod.MOVING_AVERAGE.requiresMinimumPoints <
                SmoothingMethod.SAVITZKY_GOLAY.requiresMinimumPoints,
        )
    }

    @Test
    fun `recommend handles very large dataset`() {
        val method = SmoothingMethod.recommend(dataSize = 10_000, needsSpeed = false)
        assertEquals(SmoothingMethod.SAVITZKY_GOLAY, method)
    }

    @Test
    fun `recommend handles single data point`() {
        val method = SmoothingMethod.recommend(dataSize = 1, needsSpeed = false)
        assertEquals(SmoothingMethod.EXPONENTIAL_SMOOTHING, method)
    }
}
