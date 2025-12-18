package io.lamco.netkit.exporttools.model

/**
 * Result of export validation operations.
 *
 * Provides comprehensive validation results including success/failure status, detailed error
 * messages, warnings, and suggestions for fixing issues. Validation results are immutable
 * and can be aggregated from multiple validation steps.
 *
 * ## Validation Levels
 * - **Errors**: Critical issues that prevent export (must be fixed)
 * - **Warnings**: Non-critical issues that may affect quality
 * - **Info**: Informational messages about validation
 *
 * ## Validation Categories
 * - **Schema**: Data structure and type validation
 * - **Format**: Format-specific validation (JSON, CSV, etc.)
 * - **Content**: Data content and value validation
 * - **Size**: File size and data volume validation
 * - **Security**: Security and compliance validation
 *
 * ## Usage Examples
 *
 * ### Success Result
 * ```kotlin
 * val result = ValidationResult.success()
 * if (result.isValid) {
 *     // Proceed with export
 * }
 * ```
 *
 * ### Failure Result
 * ```kotlin
 * val result = ValidationResult.failure(
 *     errors = listOf(
 *         ValidationError.schema("Missing required field: timestamp")
 *     )
 * )
 * if (!result.isValid) {
 *     println(result.summary())
 * }
 * ```
 *
 * ### Aggregated Results
 * ```kotlin
 * val result1 = validateSchema()
 * val result2 = validateContent()
 * val combined = ValidationResult.combine(result1, result2)
 * ```
 *
 * @property isValid Whether validation passed (no errors)
 * @property errors List of validation errors (critical issues)
 * @property warnings List of validation warnings (non-critical)
 * @property info List of informational messages
 * @property validatedAt Timestamp when validation was performed
 * @property durationMillis Time taken for validation in milliseconds
 *
 * @since 1.0.0
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<ValidationIssue> = emptyList(),
    val warnings: List<ValidationIssue> = emptyList(),
    val info: List<ValidationIssue> = emptyList(),
    val validatedAt: Long = System.currentTimeMillis(),
    val durationMillis: Long = 0,
) {
    init {
        require(durationMillis >= 0) { "Duration cannot be negative" }
        require((errors.isEmpty() && isValid) || (errors.isNotEmpty() && !isValid)) {
            "isValid must be false when errors are present, and true when no errors"
        }
    }

    /**
     * Whether validation failed (has errors).
     */
    val isInvalid: Boolean
        get() = !isValid

    /**
     * Total number of issues (errors + warnings + info).
     */
    val totalIssues: Int
        get() = errors.size + warnings.size + info.size

    /**
     * Whether there are any warnings.
     */
    val hasWarnings: Boolean
        get() = warnings.isNotEmpty()

    /**
     * Whether there are any informational messages.
     */
    val hasInfo: Boolean
        get() = info.isNotEmpty()

    /**
     * Returns all issues sorted by severity (errors, warnings, info).
     */
    fun allIssues(): List<ValidationIssue> = errors + warnings + info

    /**
     * Returns issues grouped by category.
     */
    fun issuesByCategory(): Map<ValidationCategory, List<ValidationIssue>> = allIssues().groupBy { it.category }

    /**
     * Returns a human-readable summary of validation results.
     */
    fun summary(): String =
        buildString {
            appendLine(if (isValid) "✓ Validation passed" else "✗ Validation failed")
            if (errors.isNotEmpty()) {
                appendLine("  Errors: ${errors.size}")
                errors.forEach { appendLine("    - ${it.message}") }
            }
            if (warnings.isNotEmpty()) {
                appendLine("  Warnings: ${warnings.size}")
                warnings.forEach { appendLine("    - ${it.message}") }
            }
            if (info.isNotEmpty()) {
                appendLine("  Info: ${info.size}")
                info.forEach { appendLine("    - ${it.message}") }
            }
            appendLine("  Duration: ${durationMillis}ms")
        }.trim()

    /**
     * Returns a detailed report with all information.
     */
    fun detailedReport(): String =
        buildString {
            appendLine("=== Validation Report ===")
            appendLine("Status: ${if (isValid) "PASSED" else "FAILED"}")
            appendLine("Timestamp: $validatedAt")
            appendLine("Duration: ${durationMillis}ms")
            appendLine()

            if (errors.isNotEmpty()) {
                appendLine("ERRORS (${errors.size}):")
                errors.forEach { issue ->
                    appendLine("  [${issue.category}] ${issue.message}")
                    issue.field?.let { appendLine("    Field: $it") }
                    issue.suggestion?.let { appendLine("    Suggestion: $it") }
                }
                appendLine()
            }

            if (warnings.isNotEmpty()) {
                appendLine("WARNINGS (${warnings.size}):")
                warnings.forEach { issue ->
                    appendLine("  [${issue.category}] ${issue.message}")
                    issue.suggestion?.let { appendLine("    Suggestion: $it") }
                }
                appendLine()
            }

            if (info.isNotEmpty()) {
                appendLine("INFO (${info.size}):")
                info.forEach { issue ->
                    appendLine("  [${issue.category}] ${issue.message}")
                }
                appendLine()
            }

            appendLine("=== End Report ===")
        }.trim()

    /**
     * Throws an exception if validation failed.
     *
     * @throws ValidationException if validation failed
     */
    fun throwIfInvalid() {
        if (isInvalid) {
            throw ValidationException(this)
        }
    }

    companion object {
        /**
         * Creates a successful validation result.
         *
         * @param warnings Optional warnings
         * @param info Optional info messages
         * @param durationMillis Validation duration
         * @return Success result
         */
        fun success(
            warnings: List<ValidationIssue> = emptyList(),
            info: List<ValidationIssue> = emptyList(),
            durationMillis: Long = 0,
        ) = ValidationResult(
            isValid = true,
            errors = emptyList(),
            warnings = warnings,
            info = info,
            durationMillis = durationMillis,
        )

        /**
         * Creates a failed validation result.
         *
         * @param errors Validation errors (required)
         * @param warnings Optional warnings
         * @param info Optional info messages
         * @param durationMillis Validation duration
         * @return Failure result
         */
        fun failure(
            errors: List<ValidationIssue>,
            warnings: List<ValidationIssue> = emptyList(),
            info: List<ValidationIssue> = emptyList(),
            durationMillis: Long = 0,
        ): ValidationResult {
            require(errors.isNotEmpty()) { "Failure result must have at least one error" }
            return ValidationResult(
                isValid = false,
                errors = errors,
                warnings = warnings,
                info = info,
                durationMillis = durationMillis,
            )
        }

        /**
         * Creates a failed validation result with a single error.
         *
         * @param error Validation error
         * @return Failure result
         */
        fun failure(error: ValidationIssue) = failure(listOf(error))

        /**
         * Combines multiple validation results.
         *
         * The combined result is valid only if ALL results are valid.
         * Errors, warnings, and info are aggregated.
         *
         * @param results Validation results to combine
         * @return Combined result
         */
        fun combine(vararg results: ValidationResult): ValidationResult = combine(results.toList())

        /**
         * Combines a list of validation results.
         *
         * @param results Validation results to combine
         * @return Combined result
         */
        fun combine(results: List<ValidationResult>): ValidationResult {
            if (results.isEmpty()) {
                return success()
            }

            val allErrors = results.flatMap { it.errors }
            val allWarnings = results.flatMap { it.warnings }
            val allInfo = results.flatMap { it.info }
            val totalDuration = results.sumOf { it.durationMillis }

            return if (allErrors.isEmpty()) {
                success(
                    warnings = allWarnings,
                    info = allInfo,
                    durationMillis = totalDuration,
                )
            } else {
                failure(
                    errors = allErrors,
                    warnings = allWarnings,
                    info = allInfo,
                    durationMillis = totalDuration,
                )
            }
        }
    }
}

/**
 * Individual validation issue (error, warning, or info).
 *
 * @property severity Issue severity level
 * @property category Issue category
 * @property message Human-readable error message
 * @property field Optional field name that caused the issue
 * @property value Optional problematic value
 * @property suggestion Optional suggestion for fixing the issue
 * @property code Optional error code for programmatic handling
 *
 * @since 1.0.0
 */
data class ValidationIssue(
    val severity: Severity,
    val category: ValidationCategory,
    val message: String,
    val field: String? = null,
    val value: String? = null,
    val suggestion: String? = null,
    val code: String? = null,
) {
    init {
        require(message.isNotBlank()) { "Message cannot be blank" }
    }

    /**
     * Issue severity level.
     */
    enum class Severity {
        /** Critical error - prevents export */
        ERROR,

        /** Warning - may affect quality */
        WARNING,

        /** Informational message */
        INFO,
    }

    /**
     * Returns a formatted message with all details.
     */
    fun formattedMessage(): String =
        buildString {
            append("[$severity] [$category] $message")
            field?.let { append(" (field: $it)") }
            value?.let { append(" (value: $it)") }
            suggestion?.let { append(" - Suggestion: $it") }
            code?.let { append(" [code: $it]") }
        }

    companion object {
        /**
         * Creates an error issue.
         */
        fun error(
            category: ValidationCategory,
            message: String,
            field: String? = null,
            value: String? = null,
            suggestion: String? = null,
            code: String? = null,
        ) = ValidationIssue(
            severity = Severity.ERROR,
            category = category,
            message = message,
            field = field,
            value = value,
            suggestion = suggestion,
            code = code,
        )

        /**
         * Creates a warning issue.
         */
        fun warning(
            category: ValidationCategory,
            message: String,
            field: String? = null,
            value: String? = null,
            suggestion: String? = null,
            code: String? = null,
        ) = ValidationIssue(
            severity = Severity.WARNING,
            category = category,
            message = message,
            field = field,
            value = value,
            suggestion = suggestion,
            code = code,
        )

        /**
         * Creates an info issue.
         */
        fun info(
            category: ValidationCategory,
            message: String,
            field: String? = null,
            suggestion: String? = null,
        ) = ValidationIssue(
            severity = Severity.INFO,
            category = category,
            message = message,
            field = field,
            value = null,
            suggestion = suggestion,
            code = null,
        )

        // Convenience factory methods for common errors

        /**
         * Schema validation error.
         */
        fun schema(
            message: String,
            field: String? = null,
            suggestion: String? = null,
        ) = error(ValidationCategory.SCHEMA, message, field = field, suggestion = suggestion)

        /**
         * Format validation error.
         */
        fun format(
            message: String,
            field: String? = null,
            suggestion: String? = null,
        ) = error(ValidationCategory.FORMAT, message, field = field, suggestion = suggestion)

        /**
         * Content validation error.
         */
        fun content(
            message: String,
            field: String? = null,
            value: String? = null,
            suggestion: String? = null,
        ) = error(ValidationCategory.CONTENT, message, field = field, value = value, suggestion = suggestion)

        /**
         * Size validation error.
         */
        fun size(
            message: String,
            suggestion: String? = null,
        ) = error(ValidationCategory.SIZE, message, suggestion = suggestion)

        /**
         * Security validation error.
         */
        fun security(
            message: String,
            suggestion: String? = null,
        ) = error(ValidationCategory.SECURITY, message, suggestion = suggestion)
    }
}

/**
 * Validation category for organizing issues.
 *
 * @property displayName Human-readable category name
 * @property description Category description
 *
 * @since 1.0.0
 */
enum class ValidationCategory(
    val displayName: String,
    val description: String,
) {
    /**
     * Schema validation (structure, types, required fields).
     */
    SCHEMA(
        displayName = "Schema",
        description = "Data structure and type validation",
    ),

    /**
     * Format validation (JSON, CSV, PDF specific rules).
     */
    FORMAT(
        displayName = "Format",
        description = "Format-specific validation rules",
    ),

    /**
     * Content validation (values, ranges, constraints).
     */
    CONTENT(
        displayName = "Content",
        description = "Data content and value validation",
    ),

    /**
     * Size validation (file size, data volume limits).
     */
    SIZE(
        displayName = "Size",
        description = "File size and data volume validation",
    ),

    /**
     * Security validation (sensitive data, compliance).
     */
    SECURITY(
        displayName = "Security",
        description = "Security and compliance validation",
    ),

    /**
     * Configuration validation (export settings).
     */
    CONFIGURATION(
        displayName = "Configuration",
        description = "Export configuration validation",
    ),

    /**
     * Template validation (report structure).
     */
    TEMPLATE(
        displayName = "Template",
        description = "Report template validation",
    ),

    /**
     * Filter validation (data filtering rules).
     */
    FILTER(
        displayName = "Filter",
        description = "Data filter validation",
    ),

    /**
     * General validation (uncategorized).
     */
    GENERAL(
        displayName = "General",
        description = "General validation",
    ),
}

/**
 * Exception thrown when validation fails.
 *
 * @property result The validation result that failed
 *
 * @since 1.0.0
 */
class ValidationException(
    val result: ValidationResult,
) : Exception(result.summary()) {
    init {
        require(!result.isValid) { "Cannot create ValidationException for valid result" }
    }

    /**
     * Returns the detailed validation report.
     */
    fun detailedMessage(): String = result.detailedReport()
}
