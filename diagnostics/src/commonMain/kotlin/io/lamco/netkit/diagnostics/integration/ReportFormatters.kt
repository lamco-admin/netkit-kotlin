package io.lamco.netkit.diagnostics.integration

import java.text.SimpleDateFormat
import java.util.*
import kotlin.time.Duration

/**
 * Shared formatting utilities for report generation.
 *
 * Provides consistent formatting across all report formats (text, markdown, JSON).
 *
 * @since 1.0.0
 */
internal object ReportFormatters {
    /**
     * Format timestamp with optional milliseconds.
     *
     * @param timestamp Unix timestamp in milliseconds
     * @param includeMillis Whether to include milliseconds
     * @return Formatted timestamp string
     */
    fun formatTimestamp(
        timestamp: Long,
        includeMillis: Boolean = false,
    ): String {
        val pattern = if (includeMillis) "yyyy-MM-dd HH:mm:ss.SSS" else "yyyy-MM-dd HH:mm:ss"
        val formatter = SimpleDateFormat(pattern, Locale.US)
        return formatter.format(Date(timestamp))
    }

    /**
     * Format duration in human-readable format.
     *
     * @param duration Duration to format
     * @return Formatted duration string (µs, ms, or s)
     */
    fun formatDuration(duration: Duration): String =
        when {
            duration.inWholeMilliseconds < 1 -> "${duration.inWholeMicroseconds}µs"
            duration.inWholeMilliseconds < 1000 -> "${duration.inWholeMilliseconds}ms"
            else -> "${"%.2f".format(duration.inWholeMilliseconds / 1000.0)}s"
        }
}
