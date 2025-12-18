package io.lamco.netkit.exporttools.model

/**
 * Result of export operation.
 *
 * @property success Whether export succeeded
 * @property content Exported content (if successful)
 * @property format Export format
 * @property sizeBytes Size of exported content in bytes
 * @property durationMillis Export duration in milliseconds
 * @property validation Validation result
 * @property error Error message (if failed)
 *
 * @since 1.0.0
 */
data class ExportResult(
    val success: Boolean,
    val content: String? = null,
    val format: ExportFormat,
    val sizeBytes: Long = 0,
    val durationMillis: Long = 0,
    val validation: ValidationResult = ValidationResult.success(),
    val error: String? = null,
) {
    init {
        require((success && content != null) || (!success && error != null)) {
            "Success requires content, failure requires error"
        }
        require(sizeBytes >= 0) { "Size cannot be negative" }
        require(durationMillis >= 0) { "Duration cannot be negative" }
    }

    /**
     * Throws exception if export failed.
     */
    fun throwIfFailed() {
        if (!success) {
            throw ExportException(error ?: "Export failed", format, validation)
        }
    }

    companion object {
        /**
         * Creates success result.
         */
        fun success(
            content: String,
            format: ExportFormat,
            sizeBytes: Long,
            durationMillis: Long,
            validation: ValidationResult = ValidationResult.success(),
        ) = ExportResult(
            success = true,
            content = content,
            format = format,
            sizeBytes = sizeBytes,
            durationMillis = durationMillis,
            validation = validation,
            error = null,
        )

        /**
         * Creates failure result.
         */
        fun failure(
            format: ExportFormat,
            error: String,
            validation: ValidationResult =
                ValidationResult.failure(
                    ValidationIssue.format(error),
                ),
        ) = ExportResult(
            success = false,
            content = null,
            format = format,
            sizeBytes = 0,
            durationMillis = 0,
            validation = validation,
            error = error,
        )
    }
}

/**
 * Exception thrown when export fails.
 *
 * @property message Error message
 * @property format Export format that failed
 * @property validation Validation result
 *
 * @since 1.0.0
 */
class ExportException(
    message: String,
    val format: ExportFormat,
    val validation: ValidationResult,
) : Exception(message)
