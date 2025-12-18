package io.lamco.netkit.analytics.model

/**
 * Time interval enumeration for time-series aggregation
 *
 * Defines standard time buckets for aggregating WiFi metrics over different time periods.
 * Used by TimeSeriesAnalyzer to create hourly, daily, weekly, or monthly rollups of
 * network performance data.
 *
 * ## Usage Example
 * ```kotlin
 * val analyzer = TimeSeriesAnalyzer()
 * val dailyMetrics = analyzer.aggregate(snapshots, TimeInterval.DAILY)
 * val weeklyMetrics = analyzer.aggregate(snapshots, TimeInterval.WEEKLY)
 * ```
 *
 * ## Time Bucket Boundaries
 * - **HOURLY**: Aligned to clock hours (e.g., 14:00:00 to 14:59:59)
 * - **DAILY**: Aligned to calendar days (midnight to midnight)
 * - **WEEKLY**: Aligned to weeks (Monday 00:00 to Sunday 23:59)
 * - **MONTHLY**: Aligned to calendar months (1st to last day)
 *
 * @property displayName Human-readable interval name
 * @property durationMillis Approximate duration in milliseconds (for hourly/daily, exact for those)
 */
enum class TimeInterval(
    val displayName: String,
    val durationMillis: Long,
) {
    /**
     * Aggregate data per hour
     *
     * Buckets are aligned to clock hours (00:00, 01:00, etc.).
     * Useful for analyzing intra-day patterns and peak usage times.
     */
    HOURLY(
        displayName = "Hourly",
        durationMillis = 3600_000L, // 1 hour
    ),

    /**
     * Aggregate data per day
     *
     * Buckets are aligned to calendar days (midnight to midnight).
     * Useful for comparing day-to-day network performance.
     */
    DAILY(
        displayName = "Daily",
        durationMillis = 86400_000L, // 24 hours
    ),

    /**
     * Aggregate data per week
     *
     * Buckets are aligned to weeks starting Monday 00:00.
     * Useful for identifying weekly patterns and long-term trends.
     */
    WEEKLY(
        displayName = "Weekly",
        durationMillis = 604800_000L, // 7 days
    ),

    /**
     * Aggregate data per month
     *
     * Buckets are aligned to calendar months (variable length: 28-31 days).
     * Useful for monthly reporting and long-term trend analysis.
     *
     * durationMillis is approximate (30 days) since months vary in length.
     */
    MONTHLY(
        displayName = "Monthly",
        durationMillis = 2592000_000L, // ~30 days (approximate)
    ),
    ;

    /**
     * Whether this interval is suitable for short-term analysis
     *
     * Returns true for HOURLY and DAILY intervals, which are best for
     * analyzing recent network behavior.
     */
    val isShortTerm: Boolean
        get() = this in listOf(HOURLY, DAILY)

    /**
     * Whether this interval is suitable for long-term analysis
     *
     * Returns true for WEEKLY and MONTHLY intervals, which are best for
     * identifying trends over extended periods.
     */
    val isLongTerm: Boolean
        get() = this in listOf(WEEKLY, MONTHLY)

    /**
     * Recommended minimum sample size for this interval
     *
     * Returns the minimum number of observations needed within one interval
     * bucket to produce statistically meaningful aggregations.
     */
    val recommendedMinSampleSize: Int
        get() =
            when (this) {
                HOURLY -> 10 // At least 10 measurements per hour
                DAILY -> 24 // At least 24 measurements per day (hourly avg)
                WEEKLY -> 50 // At least 50 measurements per week
                MONTHLY -> 100 // At least 100 measurements per month
            }

    companion object {
        /**
         * Get the most appropriate interval for a given time span
         *
         * Automatically selects the best aggregation interval based on the
         * total duration of available data.
         *
         * @param durationMillis Total time span of the data
         * @return Most appropriate TimeInterval for this duration
         */
        fun fromDuration(durationMillis: Long): TimeInterval =
            when {
                durationMillis < DAILY.durationMillis * 2 -> HOURLY
                durationMillis < WEEKLY.durationMillis * 4 -> DAILY
                durationMillis < MONTHLY.durationMillis * 3 -> WEEKLY
                else -> MONTHLY
            }

        /**
         * Get all intervals suitable for a given time span
         *
         * Returns all intervals that can produce at least 2 complete buckets
         * from the given duration.
         *
         * @param durationMillis Total time span of the data
         * @return List of suitable TimeIntervals, ordered from shortest to longest
         */
        fun suitableIntervalsFor(durationMillis: Long): List<TimeInterval> =
            entries.filter { interval ->
                // Need at least 2 complete buckets to be useful
                durationMillis >= interval.durationMillis * 2
            }
    }
}

/**
 * Smoothing method enumeration for time-series data
 *
 * Defines algorithms for smoothing noisy time-series data to reveal underlying trends.
 * Each method has different characteristics regarding computation cost and smoothness.
 *
 * @property displayName Human-readable method name
 * @property requiresMinimumPoints Minimum data points required for this method
 */
enum class SmoothingMethod(
    val displayName: String,
    val requiresMinimumPoints: Int,
) {
    /**
     * Simple Moving Average (SMA)
     *
     * Computes the unweighted mean of the previous N data points.
     * Fast and simple, but introduces lag and doesn't respond quickly to changes.
     *
     * Formula: SMA(t) = (x[t] + x[t-1] + ... + x[t-N+1]) / N
     */
    MOVING_AVERAGE(
        displayName = "Moving Average",
        requiresMinimumPoints = 3,
    ),

    /**
     * Exponential Weighted Moving Average (EWMA)
     *
     * Gives more weight to recent observations while still considering historical data.
     * Responds faster to recent changes than simple moving average.
     *
     * Formula: EWMA(t) = α * x[t] + (1-α) * EWMA(t-1)
     * where α is the smoothing factor (typically 0.1 to 0.3)
     */
    EXPONENTIAL_SMOOTHING(
        displayName = "Exponential Smoothing",
        requiresMinimumPoints = 2,
    ),

    /**
     * Savitzky-Golay Filter
     *
     * Fits a polynomial to a sliding window of data points.
     * Preserves features like peaks and valleys better than simple averaging.
     * More computationally intensive but produces smoother, more accurate results.
     *
     * Requires odd window size (3, 5, 7, etc.) and polynomial order (typically 2-4).
     */
    SAVITZKY_GOLAY(
        displayName = "Savitzky-Golay",
        requiresMinimumPoints = 5,
    ),
    ;

    /**
     * Whether this method is computationally fast
     *
     * Returns true for simple methods that can process large datasets quickly.
     */
    val isFast: Boolean
        get() = this in listOf(MOVING_AVERAGE, EXPONENTIAL_SMOOTHING)

    /**
     * Whether this method preserves signal features well
     *
     * Returns true for methods that maintain peaks, valleys, and inflection points.
     */
    val preservesFeatures: Boolean
        get() = this == SAVITZKY_GOLAY

    companion object {
        /**
         * Recommend smoothing method based on data characteristics
         *
         * @param dataSize Number of data points available
         * @param needsSpeed Whether fast computation is priority
         * @return Recommended SmoothingMethod
         */
        fun recommend(
            dataSize: Int,
            needsSpeed: Boolean = false,
        ): SmoothingMethod =
            when {
                dataSize < SAVITZKY_GOLAY.requiresMinimumPoints -> EXPONENTIAL_SMOOTHING
                needsSpeed -> MOVING_AVERAGE
                else -> SAVITZKY_GOLAY
            }
    }
}
