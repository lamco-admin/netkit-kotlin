package io.lamco.netkit.logging

/**
 * Logging interface for WiFi Intelligence libraries.
 *
 * NetKitLogger provides a platform-independent logging abstraction that allows users
 * to integrate their preferred logging framework (SLF4J, Log4j, Logback, etc.) or
 * use the default no-op implementation.
 *
 * ## Design Philosophy
 *
 * - **Zero dependencies**: No logging framework required by default
 * - **User-controlled**: Applications decide what/how to log
 * - **Simple API**: Four standard log levels (debug, info, warn, error)
 * - **Exception support**: Optional throwable parameter for error context
 *
 * ## Usage - Default (No Logging)
 *
 * By default, NetKit uses a no-op logger that discards all log messages:
 *
 * ```kotlin
 * // Default behavior - logs are discarded
 * val executor = PingExecutor()
 * executor.executePing("8.8.8.8") // No logs
 * ```
 *
 * ## Usage - Custom Logger
 *
 * Applications can provide their own logger implementation:
 *
 * ```kotlin
 * // Example: SLF4J integration
 * class Slf4jNetKitLogger(private val slf4jLogger: org.slf4j.Logger) : NetKitLogger {
 *     override fun debug(message: String, throwable: Throwable?) {
 *         if (throwable != null) slf4jLogger.debug(message, throwable)
 *         else slf4jLogger.debug(message)
 *     }
 *
 *     override fun info(message: String, throwable: Throwable?) {
 *         if (throwable != null) slf4jLogger.info(message, throwable)
 *         else slf4jLogger.info(message)
 *     }
 *
 *     override fun warn(message: String, throwable: Throwable?) {
 *         if (throwable != null) slf4jLogger.warn(message, throwable)
 *         else slf4jLogger.warn(message)
 *     }
 *
 *     override fun error(message: String, throwable: Throwable?) {
 *         if (throwable != null) slf4jLogger.error(message, throwable)
 *         else slf4jLogger.error(message)
 *     }
 * }
 *
 * // Set logger globally
 * NetKit.logger = Slf4jNetKitLogger(LoggerFactory.getLogger("NetKit"))
 * ```
 *
 * ## Usage - Console Logger (Development)
 *
 * For development/debugging, use the built-in console logger:
 *
 * ```kotlin
 * NetKit.logger = ConsoleLogger(minLevel = LogLevel.DEBUG)
 * ```
 *
 * ## Thread Safety
 *
 * Implementations MUST be thread-safe. The logger instance is accessed from
 * potentially multiple threads concurrently.
 *
 * @since 0.1.0
 */
interface NetKitLogger {
    /**
     * Log a debug message.
     *
     * Debug messages provide detailed diagnostic information useful during
     * development and troubleshooting.
     *
     * @param message The log message
     * @param throwable Optional throwable for additional context
     */
    fun debug(
        message: String,
        throwable: Throwable? = null,
    )

    /**
     * Log an info message.
     *
     * Info messages indicate normal operational events (e.g., "Starting ping test",
     * "Traceroute completed in 2.3s").
     *
     * @param message The log message
     * @param throwable Optional throwable for additional context
     */
    fun info(
        message: String,
        throwable: Throwable? = null,
    )

    /**
     * Log a warning message.
     *
     * Warning messages indicate potential issues that don't prevent operation
     * (e.g., "Timeout on probe 5 of 10", "DNS resolution slow: 800ms").
     *
     * @param message The log message
     * @param throwable Optional throwable for additional context
     */
    fun warn(
        message: String,
        throwable: Throwable? = null,
    )

    /**
     * Log an error message.
     *
     * Error messages indicate failures requiring attention (e.g., "Ping test failed",
     * "Unable to parse traceroute output").
     *
     * @param message The log message
     * @param throwable Optional throwable for additional context
     */
    fun error(
        message: String,
        throwable: Throwable? = null,
    )
}

/**
 * Log level enumeration for filtering.
 *
 * @property DEBUG Most verbose - all messages
 * @property INFO Informational messages and above
 * @property WARN Warnings and errors only
 * @property ERROR Errors only
 * @property NONE Disable all logging
 */
enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
    NONE,
    ;

    /**
     * Check if this level should log given target level.
     *
     * @param target The level to check
     * @return true if target should be logged at this level
     */
    fun shouldLog(target: LogLevel): Boolean =
        when (this) {
            NONE -> false
            DEBUG -> true
            INFO -> target >= INFO
            WARN -> target >= WARN
            ERROR -> target == ERROR
        }
}

/**
 * No-op logger implementation that discards all log messages.
 *
 * This is the default logger used by NetKit when no custom logger is configured.
 * All methods are empty, resulting in zero overhead.
 *
 * @since 0.1.0
 */
object NoOpLogger : NetKitLogger {
    override fun debug(
        message: String,
        throwable: Throwable?,
    ) {}

    override fun info(
        message: String,
        throwable: Throwable?,
    ) {}

    override fun warn(
        message: String,
        throwable: Throwable?,
    ) {}

    override fun error(
        message: String,
        throwable: Throwable?,
    ) {}
}

/**
 * Console logger implementation for development and testing.
 *
 * Prints log messages to standard output/error with level prefixes and timestamps.
 *
 * ## Output Format
 *
 * ```
 * [2025-12-18 13:45:23.456] [DEBUG] Starting ping test to 8.8.8.8
 * [2025-12-18 13:45:23.567] [INFO ] Ping completed: avg=8ms
 * [2025-12-18 13:45:23.678] [WARN ] Packet loss detected: 2/10 packets
 * [2025-12-18 13:45:23.789] [ERROR] Ping failed: Timeout
 * ```
 *
 * ## Usage
 *
 * ```kotlin
 * // Development mode - see all logs
 * NetKit.logger = ConsoleLogger(minLevel = LogLevel.DEBUG)
 *
 * // Production mode - errors only
 * NetKit.logger = ConsoleLogger(minLevel = LogLevel.ERROR)
 * ```
 *
 * @property minLevel Minimum log level to output
 * @property includeTimestamp Whether to include timestamps
 * @property includeThreadName Whether to include thread names
 *
 * @since 0.1.0
 */
class ConsoleLogger(
    private val minLevel: LogLevel = LogLevel.INFO,
    private val includeTimestamp: Boolean = true,
    private val includeThreadName: Boolean = false,
) : NetKitLogger {
    override fun debug(
        message: String,
        throwable: Throwable?,
    ) {
        if (minLevel.shouldLog(LogLevel.DEBUG)) {
            log("DEBUG", message, throwable, ::println)
        }
    }

    override fun info(
        message: String,
        throwable: Throwable?,
    ) {
        if (minLevel.shouldLog(LogLevel.INFO)) {
            log("INFO ", message, throwable, ::println)
        }
    }

    override fun warn(
        message: String,
        throwable: Throwable?,
    ) {
        if (minLevel.shouldLog(LogLevel.WARN)) {
            log("WARN ", message, throwable, System.err::println)
        }
    }

    override fun error(
        message: String,
        throwable: Throwable?,
    ) {
        if (minLevel.shouldLog(LogLevel.ERROR)) {
            log("ERROR", message, throwable, System.err::println)
        }
    }

    private fun log(
        level: String,
        message: String,
        throwable: Throwable?,
        output: (String) -> Unit,
    ) {
        val prefix =
            buildString {
                if (includeTimestamp) {
                    append("[${currentTimestamp()}] ")
                }
                append("[$level] ")
                if (includeThreadName) {
                    append("[${Thread.currentThread().name}] ")
                }
            }

        output("$prefix$message")

        throwable?.let {
            output("$prefix  Exception: ${it.javaClass.simpleName}: ${it.message}")
            it.stackTrace.take(5).forEach { frame ->
                output("$prefix    at $frame")
            }
            if (it.stackTrace.size > 5) {
                output("$prefix    ... ${it.stackTrace.size - 5} more")
            }
        }
    }

    private fun currentTimestamp(): String {
        val now = System.currentTimeMillis()
        val seconds = now / 1000
        val millis = now % 1000
        return String.format("%tF %<tT.%03d", seconds * 1000, millis)
    }
}

/**
 * Global NetKit configuration.
 *
 * Provides application-wide settings for all NetKit libraries.
 *
 * ## Thread Safety
 *
 * The logger property uses a volatile field for safe publication across threads.
 * However, applications should set the logger once during initialization before
 * using any NetKit APIs.
 *
 * @since 0.1.0
 */
object NetKit {
    /**
     * Global logger instance used by all NetKit libraries.
     *
     * Defaults to [NoOpLogger] which discards all log messages.
     *
     * ## Usage
     *
     * ```kotlin
     * // Set logger during application initialization
     * NetKit.logger = ConsoleLogger(minLevel = LogLevel.DEBUG)
     *
     * // Or integrate with SLF4J
     * NetKit.logger = Slf4jNetKitLogger(LoggerFactory.getLogger("NetKit"))
     * ```
     *
     * **Important:** Set the logger BEFORE using any NetKit APIs to ensure
     * all log messages are captured.
     */
    @Volatile
    var logger: NetKitLogger = NoOpLogger
        set(value) {
            field = value
            field.info("NetKit logger configured: ${value::class.simpleName}")
        }
}
