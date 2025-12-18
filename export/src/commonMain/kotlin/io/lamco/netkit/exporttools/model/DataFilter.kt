package io.lamco.netkit.exporttools.model

import java.time.Duration
import java.time.Instant

/**
 * Comprehensive data filtering for export operations.
 *
 * Filters allow selecting specific subsets of data for export based on various criteria
 * including time ranges, network identifiers, performance metrics, security levels, and custom rules.
 *
 * Filters are composable using boolean logic (AND/OR/NOT) and can be nested for complex queries.
 *
 * ## Filter Categories
 * - **Temporal**: Time ranges, collection periods
 * - **Network**: SSIDs, BSSIDs, manufacturers
 * - **Security**: Security types, vulnerability levels
 * - **Performance**: RSSI, SNR, bandwidth thresholds
 * - **Topology**: AP counts, roaming domains
 * - **Location**: Geographic or logical locations
 * - **Custom**: User-defined predicates
 *
 * ## Usage Examples
 *
 * ### Simple Filter
 * ```kotlin
 * val filter = DataFilter.Builder()
 *     .timeRange(startTime, endTime)
 *     .networks(listOf("MyWiFi", "GuestWiFi"))
 *     .build()
 * ```
 *
 * ### Complex Filter with Thresholds
 * ```kotlin
 * val filter = DataFilter.Builder()
 *     .timeRange(startTime, endTime)
 *     .minRssi(-70)
 *     .minSecurityLevel(SecurityLevel.WPA2)
 *     .excludeHidden(true)
 *     .build()
 * ```
 *
 * ### Composite Filter (AND/OR logic)
 * ```kotlin
 * val filter1 = DataFilter.Builder().networks(listOf("WiFi1")).build()
 * val filter2 = DataFilter.Builder().minRssi(-60).build()
 * val combined = DataFilter.and(filter1, filter2)
 * ```
 *
 * @since 1.0.0
 */
sealed class DataFilter {
    /**
     * Returns a human-readable description of this filter.
     */
    abstract fun describe(): String

    /**
     * Returns whether this filter is empty (no criteria specified).
     */
    abstract fun isEmpty(): Boolean

    /**
     * Returns the number of filter criteria specified.
     */
    abstract fun criteriaCount(): Int

    /**
     * No filter - passes all data through.
     */
    data object NoFilter : DataFilter() {
        override fun describe() = "No filtering (all data)"

        override fun isEmpty() = true

        override fun criteriaCount() = 0
    }

    /**
     * Criteria-based filter with specific rules.
     *
     * @property timeRange Optional time range filter
     * @property networks Optional list of SSIDs to include (null = all)
     * @property bssids Optional list of BSSIDs to include (null = all)
     * @property manufacturers Optional list of manufacturers to include (null = all)
     * @property excludeNetworks Optional list of SSIDs to exclude
     * @property excludeBssids Optional list of BSSIDs to exclude
     * @property minRssi Minimum RSSI threshold in dBm (null = no minimum)
     * @property maxRssi Maximum RSSI threshold in dBm (null = no maximum)
     * @property minSnr Minimum SNR threshold in dB (null = no minimum)
     * @property minSecurityLevel Minimum security level (null = any)
     * @property maxSecurityLevel Maximum security level (null = any)
     * @property securityTypes Specific security types to include (null = all)
     * @property excludeHidden Exclude hidden networks (empty/null SSID)
     * @property excludeOpen Exclude open/unsecured networks
     * @property minBandwidth Minimum bandwidth in Mbps (null = no minimum)
     * @property maxLatency Maximum latency in ms (null = no maximum)
     * @property maxPacketLoss Maximum packet loss percentage (null = no maximum)
     * @property bands Frequency bands to include (2.4GHz, 5GHz, 6GHz)
     * @property minApCount Minimum number of APs for multi-AP networks
     * @property locations Specific locations to include (null = all)
     * @property tags Custom tags to filter by (null = all)
     * @property limit Maximum number of results (null = unlimited)
     */
    data class Criteria(
        val timeRange: TimeRange? = null,
        val networks: List<String>? = null,
        val bssids: List<String>? = null,
        val manufacturers: List<String>? = null,
        val excludeNetworks: List<String>? = null,
        val excludeBssids: List<String>? = null,
        val minRssi: Int? = null,
        val maxRssi: Int? = null,
        val minSnr: Double? = null,
        val minSecurityLevel: SecurityLevel? = null,
        val maxSecurityLevel: SecurityLevel? = null,
        val securityTypes: List<String>? = null,
        val excludeHidden: Boolean = false,
        val excludeOpen: Boolean = false,
        val minBandwidth: Double? = null,
        val maxLatency: Int? = null,
        val maxPacketLoss: Double? = null,
        val bands: List<Band>? = null,
        val minApCount: Int? = null,
        val locations: List<String>? = null,
        val tags: List<String>? = null,
        val limit: Int? = null,
    ) : DataFilter() {
        init {
            // Validate RSSI range
            minRssi?.let {
                require(it in RSSI_MIN..RSSI_MAX) {
                    "minRssi must be in range $RSSI_MIN..$RSSI_MAX dBm, got: $it"
                }
            }
            maxRssi?.let {
                require(it in RSSI_MIN..RSSI_MAX) {
                    "maxRssi must be in range $RSSI_MIN..RSSI_MAX dBm, got: $it"
                }
            }
            if (minRssi != null && maxRssi != null) {
                require(minRssi <= maxRssi) {
                    "minRssi ($minRssi) cannot be greater than maxRssi ($maxRssi)"
                }
            }

            // Validate SNR
            minSnr?.let {
                require(it >= 0.0) { "minSnr must be non-negative, got: $it" }
            }

            // Validate bandwidth
            minBandwidth?.let {
                require(it > 0.0) { "minBandwidth must be positive, got: $it Mbps" }
            }

            // Validate latency
            maxLatency?.let {
                require(it > 0) { "maxLatency must be positive, got: $it ms" }
            }

            // Validate packet loss
            maxPacketLoss?.let {
                require(it in 0.0..100.0) {
                    "maxPacketLoss must be in range 0..100%, got: $it"
                }
            }

            // Validate AP count
            minApCount?.let {
                require(it > 0) { "minApCount must be positive, got: $it" }
            }

            // Validate limit
            limit?.let {
                require(it > 0) { "limit must be positive, got: $it" }
            }

            // Validate security level ordering
            if (minSecurityLevel != null && maxSecurityLevel != null) {
                require(minSecurityLevel.level <= maxSecurityLevel.level) {
                    "minSecurityLevel ($minSecurityLevel) cannot be higher than maxSecurityLevel ($maxSecurityLevel)"
                }
            }

            // Validate list constraints
            networks?.let { require(it.isNotEmpty()) { "networks list cannot be empty" } }
            bssids?.let { require(it.isNotEmpty()) { "bssids list cannot be empty" } }
            manufacturers?.let { require(it.isNotEmpty()) { "manufacturers list cannot be empty" } }
            securityTypes?.let { require(it.isNotEmpty()) { "securityTypes list cannot be empty" } }
            bands?.let { require(it.isNotEmpty()) { "bands list cannot be empty" } }
            locations?.let { require(it.isNotEmpty()) { "locations list cannot be empty" } }
            tags?.let { require(it.isNotEmpty()) { "tags list cannot be empty" } }
        }

        override fun describe(): String {
            val criteria = mutableListOf<String>()

            timeRange?.let { criteria.add("time: ${it.describe()}") }
            networks?.let { criteria.add("networks: ${it.joinToString(", ")}") }
            bssids?.let { criteria.add("BSSIDs: ${it.size}") }
            manufacturers?.let { criteria.add("manufacturers: ${it.joinToString(", ")}") }
            excludeNetworks?.let { if (it.isNotEmpty()) criteria.add("exclude: ${it.joinToString(", ")}") }
            minRssi?.let { criteria.add("RSSI >= $it dBm") }
            maxRssi?.let { criteria.add("RSSI <= $it dBm") }
            minSnr?.let { criteria.add("SNR >= $it dB") }
            minSecurityLevel?.let { criteria.add("security >= $it") }
            securityTypes?.let { criteria.add("types: ${it.joinToString(", ")}") }
            if (excludeHidden) criteria.add("exclude hidden")
            if (excludeOpen) criteria.add("exclude open")
            minBandwidth?.let { criteria.add("bandwidth >= $it Mbps") }
            maxLatency?.let { criteria.add("latency <= $it ms") }
            maxPacketLoss?.let { criteria.add("packet loss <= $it%") }
            bands?.let { criteria.add("bands: ${it.joinToString(", ")}") }
            minApCount?.let { criteria.add("APs >= $it") }
            locations?.let { criteria.add("locations: ${it.joinToString(", ")}") }
            tags?.let { criteria.add("tags: ${it.joinToString(", ")}") }
            limit?.let { criteria.add("limit: $it") }

            return if (criteria.isEmpty()) {
                "No criteria"
            } else {
                criteria.joinToString("; ")
            }
        }

        override fun isEmpty() = criteriaCount() == 0

        override fun criteriaCount(): Int {
            var count = 0
            if (timeRange != null) count++
            if (networks != null) count++
            if (bssids != null) count++
            if (manufacturers != null) count++
            if (excludeNetworks != null && excludeNetworks.isNotEmpty()) count++
            if (excludeBssids != null && excludeBssids.isNotEmpty()) count++
            if (minRssi != null) count++
            if (maxRssi != null) count++
            if (minSnr != null) count++
            if (minSecurityLevel != null) count++
            if (maxSecurityLevel != null) count++
            if (securityTypes != null) count++
            if (excludeHidden) count++
            if (excludeOpen) count++
            if (minBandwidth != null) count++
            if (maxLatency != null) count++
            if (maxPacketLoss != null) count++
            if (bands != null) count++
            if (minApCount != null) count++
            if (locations != null) count++
            if (tags != null) count++
            if (limit != null) count++
            return count
        }
    }

    /**
     * Composite AND filter - all sub-filters must match.
     *
     * @property filters List of filters to combine with AND logic
     */
    data class And(
        val filters: List<DataFilter>,
    ) : DataFilter() {
        init {
            require(filters.size >= 2) { "AND filter requires at least 2 sub-filters" }
        }

        override fun describe() = filters.joinToString(" AND ") { "(${it.describe()})" }

        override fun isEmpty() = filters.all { it.isEmpty() }

        override fun criteriaCount() = filters.sumOf { it.criteriaCount() }
    }

    /**
     * Composite OR filter - any sub-filter can match.
     *
     * @property filters List of filters to combine with OR logic
     */
    data class Or(
        val filters: List<DataFilter>,
    ) : DataFilter() {
        init {
            require(filters.size >= 2) { "OR filter requires at least 2 sub-filters" }
        }

        override fun describe() = filters.joinToString(" OR ") { "(${it.describe()})" }

        override fun isEmpty() = filters.all { it.isEmpty() }

        override fun criteriaCount() = filters.sumOf { it.criteriaCount() }
    }

    /**
     * NOT filter - inverts the sub-filter.
     *
     * @property filter Filter to negate
     */
    data class Not(
        val filter: DataFilter,
    ) : DataFilter() {
        override fun describe() = "NOT (${filter.describe()})"

        override fun isEmpty() = filter.isEmpty()

        override fun criteriaCount() = filter.criteriaCount()
    }

    /**
     * Time range for filtering.
     *
     * @property start Start time (inclusive)
     * @property end End time (inclusive)
     */
    data class TimeRange(
        val start: Instant,
        val end: Instant,
    ) {
        init {
            require(!end.isBefore(start)) {
                "End time ($end) cannot be before start time ($start)"
            }
        }

        /**
         * Duration of this time range.
         */
        val duration: Duration
            get() = Duration.between(start, end)

        /**
         * Human-readable description.
         */
        fun describe(): String {
            val durationDesc =
                when {
                    duration.toHours() < 1 -> "${duration.toMinutes()}m"
                    duration.toDays() < 1 -> "${duration.toHours()}h"
                    else -> "${duration.toDays()}d"
                }
            return "$start to $end ($durationDesc)"
        }

        companion object {
            /**
             * Creates a time range for the last N hours.
             */
            fun lastHours(hours: Int): TimeRange {
                val end = Instant.now()
                val start = end.minus(Duration.ofHours(hours.toLong()))
                return TimeRange(start, end)
            }

            /**
             * Creates a time range for the last N days.
             */
            fun lastDays(days: Int): TimeRange {
                val end = Instant.now()
                val start = end.minus(Duration.ofDays(days.toLong()))
                return TimeRange(start, end)
            }

            /**
             * Creates a time range for today (since midnight).
             */
            fun today(): TimeRange {
                val now = Instant.now()
                val startOfDay = now.truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                return TimeRange(startOfDay, now)
            }
        }
    }

    /**
     * Security level enumeration for filtering.
     */
    enum class SecurityLevel(
        val level: Int,
    ) {
        /** No security (open networks) */
        NONE(0),

        /** WEP (deprecated, insecure) */
        WEP(1),

        /** WPA (deprecated) */
        WPA(2),

        /** WPA2 Personal */
        WPA2_PERSONAL(3),

        /** WPA2 Enterprise */
        WPA2_ENTERPRISE(4),

        /** WPA3 Personal */
        WPA3_PERSONAL(5),

        /** WPA3 Enterprise */
        WPA3_ENTERPRISE(6),
    }

    /**
     * Frequency band enumeration.
     */
    enum class Band(
        val displayName: String,
        val frequencyGHz: Double,
    ) {
        /** 2.4 GHz band (2.4-2.5 GHz) */
        BAND_2_4GHZ("2.4 GHz", 2.4),

        /** 5 GHz band (5.15-5.875 GHz) */
        BAND_5GHZ("5 GHz", 5.0),

        /** 6 GHz band (5.925-7.125 GHz, WiFi 6E) */
        BAND_6GHZ("6 GHz", 6.0),
    }

    /**
     * Builder for creating criteria-based filters with fluent API.
     */
    class Builder {
        private var timeRange: TimeRange? = null
        private var networks: List<String>? = null
        private var bssids: List<String>? = null
        private var manufacturers: List<String>? = null
        private var excludeNetworks: List<String>? = null
        private var excludeBssids: List<String>? = null
        private var minRssi: Int? = null
        private var maxRssi: Int? = null
        private var minSnr: Double? = null
        private var minSecurityLevel: SecurityLevel? = null
        private var maxSecurityLevel: SecurityLevel? = null
        private var securityTypes: List<String>? = null
        private var excludeHidden: Boolean = false
        private var excludeOpen: Boolean = false
        private var minBandwidth: Double? = null
        private var maxLatency: Int? = null
        private var maxPacketLoss: Double? = null
        private var bands: List<Band>? = null
        private var minApCount: Int? = null
        private var locations: List<String>? = null
        private var tags: List<String>? = null
        private var limit: Int? = null

        fun timeRange(
            start: Instant,
            end: Instant,
        ) = apply {
            this.timeRange = TimeRange(start, end)
        }

        fun timeRange(range: TimeRange) = apply { this.timeRange = range }

        fun lastHours(hours: Int) = apply { this.timeRange = TimeRange.lastHours(hours) }

        fun lastDays(days: Int) = apply { this.timeRange = TimeRange.lastDays(days) }

        fun today() = apply { this.timeRange = TimeRange.today() }

        fun networks(networks: List<String>) = apply { this.networks = networks }

        fun network(ssid: String) = apply { this.networks = listOf(ssid) }

        fun bssids(bssids: List<String>) = apply { this.bssids = bssids }

        fun bssid(bssid: String) = apply { this.bssids = listOf(bssid) }

        fun manufacturers(manufacturers: List<String>) = apply { this.manufacturers = manufacturers }

        fun manufacturer(manufacturer: String) = apply { this.manufacturers = listOf(manufacturer) }

        fun excludeNetworks(networks: List<String>) = apply { this.excludeNetworks = networks }

        fun excludeNetwork(ssid: String) = apply { this.excludeNetworks = listOf(ssid) }

        fun excludeBssids(bssids: List<String>) = apply { this.excludeBssids = bssids }

        fun excludeBssid(bssid: String) = apply { this.excludeBssids = listOf(bssid) }

        fun minRssi(rssi: Int) = apply { this.minRssi = rssi }

        fun maxRssi(rssi: Int) = apply { this.maxRssi = rssi }

        fun rssiRange(
            min: Int,
            max: Int,
        ) = apply {
            this.minRssi = min
            this.maxRssi = max
        }

        fun minSnr(snr: Double) = apply { this.minSnr = snr }

        fun minSecurityLevel(level: SecurityLevel) = apply { this.minSecurityLevel = level }

        fun maxSecurityLevel(level: SecurityLevel) = apply { this.maxSecurityLevel = level }

        fun securityTypes(types: List<String>) = apply { this.securityTypes = types }

        fun securityType(type: String) = apply { this.securityTypes = listOf(type) }

        fun excludeHidden(exclude: Boolean = true) = apply { this.excludeHidden = exclude }

        fun excludeOpen(exclude: Boolean = true) = apply { this.excludeOpen = exclude }

        fun minBandwidth(mbps: Double) = apply { this.minBandwidth = mbps }

        fun maxLatency(ms: Int) = apply { this.maxLatency = ms }

        fun maxPacketLoss(percent: Double) = apply { this.maxPacketLoss = percent }

        fun bands(bands: List<Band>) = apply { this.bands = bands }

        fun band(band: Band) = apply { this.bands = listOf(band) }

        fun minApCount(count: Int) = apply { this.minApCount = count }

        fun locations(locations: List<String>) = apply { this.locations = locations }

        fun location(location: String) = apply { this.locations = listOf(location) }

        fun tags(tags: List<String>) = apply { this.tags = tags }

        fun tag(tag: String) = apply { this.tags = listOf(tag) }

        fun limit(limit: Int) = apply { this.limit = limit }

        fun build(): DataFilter =
            Criteria(
                timeRange = timeRange,
                networks = networks,
                bssids = bssids,
                manufacturers = manufacturers,
                excludeNetworks = excludeNetworks,
                excludeBssids = excludeBssids,
                minRssi = minRssi,
                maxRssi = maxRssi,
                minSnr = minSnr,
                minSecurityLevel = minSecurityLevel,
                maxSecurityLevel = maxSecurityLevel,
                securityTypes = securityTypes,
                excludeHidden = excludeHidden,
                excludeOpen = excludeOpen,
                minBandwidth = minBandwidth,
                maxLatency = maxLatency,
                maxPacketLoss = maxPacketLoss,
                bands = bands,
                minApCount = minApCount,
                locations = locations,
                tags = tags,
                limit = limit,
            )
    }

    companion object {
        /** Minimum valid RSSI value in dBm */
        const val RSSI_MIN = -120

        /** Maximum valid RSSI value in dBm */
        const val RSSI_MAX = 0

        /**
         * Combines multiple filters with AND logic.
         */
        fun and(vararg filters: DataFilter): DataFilter = And(filters.toList())

        /**
         * Combines multiple filters with OR logic.
         */
        fun or(vararg filters: DataFilter): DataFilter = Or(filters.toList())

        /**
         * Negates a filter.
         */
        fun not(filter: DataFilter): DataFilter = Not(filter)

        /**
         * Creates a filter for recent data (last N hours).
         */
        fun recent(hours: Int = 24): DataFilter = Builder().lastHours(hours).build()

        /**
         * Creates a filter for high-quality networks only.
         */
        fun highQuality(): DataFilter =
            Builder()
                .minRssi(-60)
                .minSnr(20.0)
                .excludeOpen(true)
                .build()

        /**
         * Creates a filter for secure networks only.
         */
        fun secureOnly(): DataFilter =
            Builder()
                .minSecurityLevel(SecurityLevel.WPA2_PERSONAL)
                .excludeOpen(true)
                .build()
    }
}
