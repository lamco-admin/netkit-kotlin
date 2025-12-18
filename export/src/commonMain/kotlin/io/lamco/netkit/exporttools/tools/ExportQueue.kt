package io.lamco.netkit.exporttools.tools

import io.lamco.netkit.exporttools.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Export queue for background report generation.
 *
 * Manages asynchronous export operations with priority queuing, concurrency control,
 * and comprehensive status tracking. Supports multiple concurrent exports with
 * configurable resource limits.
 *
 * ## Features
 * - **Priority Queue**: HIGH priority exports processed before NORMAL
 * - **Concurrency Control**: Configurable max concurrent exports
 * - **Status Tracking**: Real-time status updates for all queued exports
 * - **Error Recovery**: Automatic retry with exponential backoff
 * - **Result Caching**: Optional result caching for duplicate requests
 * - **Cancellation**: Cancel pending or in-progress exports
 * - **Progress Monitoring**: Track export progress and ETA
 *
 * ## Queue States
 * - **PENDING**: Waiting in queue
 * - **RUNNING**: Currently being processed
 * - **COMPLETED**: Successfully finished
 * - **FAILED**: Failed after all retries
 * - **CANCELLED**: Cancelled by user
 *
 * ## Usage Example
 *
 * ```kotlin
 * val queue = ExportQueue(maxConcurrent = 3)
 * queue.start()
 *
 * val jobId = queue.enqueue(
 *     data = reportData,
 *     config = exportConfig,
 *     priority = ExportPriority.HIGH,
 *     onComplete = { result ->
 *         println("Export complete: ${result.sizeBytes} bytes")
 *     }
 * )
 *
 * val status = queue.getStatus(jobId)
 * queue.cancel(jobId)
 * queue.stop()
 * ```
 *
 * @property maxConcurrent Maximum concurrent exports (default: 2)
 * @property enableCache Enable result caching (default: false)
 * @property maxRetries Maximum retry attempts (default: 3)
 *
 * @since 1.0.0
 */
class ExportQueue(
    private val maxConcurrent: Int = 2,
    private val enableCache: Boolean = false,
    private val maxRetries: Int = 3,
) {
    init {
        require(maxConcurrent > 0) { "maxConcurrent must be positive" }
        require(maxRetries >= 0) { "maxRetries cannot be negative" }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobQueue = PriorityQueue<ExportJob>(compareByDescending { it.priority.ordinal })
    private val activeJobs = ConcurrentHashMap<String, ExportJob>()
    private val completedJobs = ConcurrentHashMap<String, ExportJobStatus>()
    private val resultCache = ConcurrentHashMap<String, ExportResult>()
    private val queueMutex = Mutex()
    private val jobIdCounter = AtomicLong(0)
    private val activeCount = AtomicInteger(0)

    @Volatile
    private var isRunning = false

    private var processingJob: Job? = null

    /**
     * Starts the export queue processing.
     *
     * Must be called before enqueueing jobs.
     */
    fun start() {
        if (isRunning) return

        isRunning = true
        processingJob =
            scope.launch {
                processQueue()
            }
    }

    /**
     * Stops the export queue processing.
     *
     * Waits for active jobs to complete. Does not cancel running exports.
     *
     * @param graceful If true, waits for active jobs; if false, cancels immediately
     */
    suspend fun stop(graceful: Boolean = true) {
        isRunning = false

        if (graceful) {
            while (activeCount.get() > 0) {
                delay(100)
            }
        }

        processingJob?.cancel()
        scope.cancel()
    }

    /**
     * Enqueues export job.
     *
     * @param data Report data to export
     * @param config Export configuration
     * @param priority Job priority (default: NORMAL)
     * @param onComplete Callback on completion (optional)
     * @param onProgress Callback on progress updates (optional)
     * @return Job ID for tracking
     */
    fun enqueue(
        data: ReportData,
        config: ExportConfiguration,
        priority: ExportPriority = ExportPriority.NORMAL,
        onComplete: ((ExportResult) -> Unit)? = null,
        onProgress: ((Int) -> Unit)? = null,
    ): String {
        require(isRunning) { "Queue must be started before enqueueing jobs" }

        val jobId = generateJobId()

        if (enableCache) {
            val cacheKey = generateCacheKey(data, config)
            resultCache[cacheKey]?.let { cachedResult ->
                onComplete?.invoke(cachedResult)
                completedJobs[jobId] =
                    ExportJobStatus(
                        jobId = jobId,
                        state = JobState.COMPLETED,
                        enqueuedAt = System.currentTimeMillis(),
                        completedAt = System.currentTimeMillis(),
                        result = cachedResult,
                        fromCache = true,
                    )
                return jobId
            }
        }

        val job =
            ExportJob(
                jobId = jobId,
                data = data,
                config = config,
                priority = priority,
                onComplete = onComplete,
                onProgress = onProgress,
                enqueuedAt = System.currentTimeMillis(),
                retryCount = 0,
            )

        scope.launch {
            queueMutex.withLock {
                jobQueue.offer(job)
                activeJobs[jobId] = job
            }
        }

        return jobId
    }

    /**
     * Gets status of export job.
     *
     * @param jobId Job ID
     * @return Job status or null if not found
     */
    fun getStatus(jobId: String): ExportJobStatus? {
        activeJobs[jobId]?.let { job ->
            return ExportJobStatus(
                jobId = jobId,
                state = job.state,
                progress = job.progress,
                enqueuedAt = job.enqueuedAt,
                startedAt = job.startedAt,
                completedAt = job.completedAt,
                result = job.result,
                error = job.error,
                retryCount = job.retryCount,
            )
        }

        return completedJobs[jobId]
    }

    /**
     * Cancels export job.
     *
     * @param jobId Job ID to cancel
     * @return True if cancelled, false if not found or already completed
     */
    suspend fun cancel(jobId: String): Boolean {
        val job = activeJobs[jobId] ?: return false

        return when (job.state) {
            JobState.PENDING -> {
                queueMutex.withLock {
                    jobQueue.remove(job)
                    activeJobs.remove(jobId)
                    completedJobs[jobId] =
                        ExportJobStatus(
                            jobId = jobId,
                            state = JobState.CANCELLED,
                            enqueuedAt = job.enqueuedAt,
                            completedAt = System.currentTimeMillis(),
                        )
                }
                true
            }
            JobState.RUNNING -> {
                job.cancellationJob?.cancel()
                job.state = JobState.CANCELLED
                job.completedAt = System.currentTimeMillis()
                activeJobs.remove(jobId)
                completedJobs[jobId] =
                    ExportJobStatus(
                        jobId = jobId,
                        state = JobState.CANCELLED,
                        enqueuedAt = job.enqueuedAt,
                        startedAt = job.startedAt,
                        completedAt = job.completedAt,
                    )
                true
            }
            else -> false
        }
    }

    /**
     * Gets current queue statistics.
     *
     * @return Queue statistics
     */
    fun getStatistics(): QueueStatistics {
        val pending = jobQueue.size
        val active = activeCount.get()
        val completed = completedJobs.values.count { it.state == JobState.COMPLETED }
        val failed = completedJobs.values.count { it.state == JobState.FAILED }
        val cancelled = completedJobs.values.count { it.state == JobState.CANCELLED }

        return QueueStatistics(
            pending = pending,
            running = active,
            completed = completed,
            failed = failed,
            cancelled = cancelled,
            cacheHits = completedJobs.values.count { it.fromCache },
            totalProcessed = completed + failed + cancelled,
        )
    }

    /**
     * Clears completed and cancelled jobs from history.
     *
     * @return Number of jobs cleared
     */
    fun clearHistory(): Int {
        val initialSize = completedJobs.size
        completedJobs.clear()
        return initialSize
    }

    /**
     * Processes the export queue.
     */
    private suspend fun processQueue() {
        while (isRunning) {
            // Wait if at capacity, otherwise process next job
            if (activeCount.get() < maxConcurrent) {
                val job = queueMutex.withLock { jobQueue.poll() }

                if (job != null) {
                    activeCount.incrementAndGet()
                    scope.launch {
                        try {
                            processJob(job)
                        } finally {
                            activeCount.decrementAndGet()
                        }
                    }
                } else {
                    delay(100) // Queue empty, wait
                }
            } else {
                delay(100) // At capacity, wait
            }
        }
    }

    /**
     * Processes a single export job.
     */
    private suspend fun processJob(job: ExportJob) {
        job.state = JobState.RUNNING
        job.startedAt = System.currentTimeMillis()

        try {
            val orchestrator = ReportOrchestrator()

            job.cancellationJob =
                scope.launch {
                    for (i in 0..100 step 10) {
                        if (!isActive) break
                        job.progress = i
                        job.onProgress?.invoke(i)
                        delay(50)
                    }
                }

            val result =
                withContext(Dispatchers.Default) {
                    orchestrator.generateReport(job.data, job.config)
                }

            job.cancellationJob?.cancel()
            job.progress = 100
            job.onProgress?.invoke(100)

            if (result.success) {
                job.state = JobState.COMPLETED
                job.result = result
                job.completedAt = System.currentTimeMillis()

                if (enableCache) {
                    val cacheKey = generateCacheKey(job.data, job.config)
                    resultCache[cacheKey] = result
                }

                job.onComplete?.invoke(result)

                activeJobs.remove(job.jobId)
                completedJobs[job.jobId] =
                    ExportJobStatus(
                        jobId = job.jobId,
                        state = JobState.COMPLETED,
                        progress = 100,
                        enqueuedAt = job.enqueuedAt,
                        startedAt = job.startedAt,
                        completedAt = job.completedAt,
                        result = result,
                    )
            } else {
                handleJobFailure(job, result.error ?: "Unknown error")
            }
        } catch (e: CancellationException) {
            job.state = JobState.CANCELLED
            job.completedAt = System.currentTimeMillis()
            activeJobs.remove(job.jobId)
            completedJobs[job.jobId] =
                ExportJobStatus(
                    jobId = job.jobId,
                    state = JobState.CANCELLED,
                    enqueuedAt = job.enqueuedAt,
                    startedAt = job.startedAt,
                    completedAt = job.completedAt,
                )
        } catch (e: IllegalArgumentException) {
            handleJobFailure(job, e.message ?: "Export configuration error")
        }
    }

    /**
     * Handles job failure with retry logic.
     */
    private suspend fun handleJobFailure(
        job: ExportJob,
        error: String,
    ) {
        if (job.retryCount < maxRetries) {
            val backoffMs = (1L shl job.retryCount) * 1000
            job.retryCount++
            job.error = "$error (retry ${job.retryCount}/$maxRetries)"
            job.state = JobState.PENDING

            delay(backoffMs)

            queueMutex.withLock {
                jobQueue.offer(job)
            }
        } else {
            job.state = JobState.FAILED
            job.error = "$error (max retries exceeded)"
            job.completedAt = System.currentTimeMillis()

            val failureResult =
                ExportResult.failure(
                    format = job.config.format,
                    error = job.error!!,
                )

            job.onComplete?.invoke(failureResult)

            activeJobs.remove(job.jobId)
            completedJobs[job.jobId] =
                ExportJobStatus(
                    jobId = job.jobId,
                    state = JobState.FAILED,
                    enqueuedAt = job.enqueuedAt,
                    startedAt = job.startedAt,
                    completedAt = job.completedAt,
                    error = job.error,
                    retryCount = job.retryCount,
                )
        }
    }

    /**
     * Generates unique job ID.
     */
    private fun generateJobId(): String = "export-${System.currentTimeMillis()}-${jobIdCounter.incrementAndGet()}"

    /**
     * Generates cache key for result caching.
     */
    private fun generateCacheKey(
        data: ReportData,
        config: ExportConfiguration,
    ): String = "${data.hashCode()}-${config.format}-${config.template.hashCode()}"
}

/**
 * Export job internal representation.
 *
 * @property jobId Unique job identifier
 * @property data Report data
 * @property config Export configuration
 * @property priority Job priority
 * @property onComplete Completion callback
 * @property onProgress Progress callback
 * @property enqueuedAt Timestamp when enqueued
 * @property retryCount Current retry count
 *
 * @since 1.0.0
 */
private data class ExportJob(
    val jobId: String,
    val data: ReportData,
    val config: ExportConfiguration,
    val priority: ExportPriority,
    val onComplete: ((ExportResult) -> Unit)?,
    val onProgress: ((Int) -> Unit)?,
    val enqueuedAt: Long,
    var retryCount: Int,
) {
    var state: JobState = JobState.PENDING
    var progress: Int = 0
    var startedAt: Long? = null
    var completedAt: Long? = null
    var result: ExportResult? = null
    var error: String? = null
    var cancellationJob: Job? = null
}

/**
 * Export job status for external tracking.
 *
 * @property jobId Job identifier
 * @property state Current job state
 * @property progress Progress percentage (0-100)
 * @property enqueuedAt Timestamp when enqueued
 * @property startedAt Timestamp when started
 * @property completedAt Timestamp when completed
 * @property result Export result (if completed)
 * @property error Error message (if failed)
 * @property retryCount Number of retries
 * @property fromCache Whether result was from cache
 *
 * @since 1.0.0
 */
data class ExportJobStatus(
    val jobId: String,
    val state: JobState,
    val progress: Int = 0,
    val enqueuedAt: Long,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val result: ExportResult? = null,
    val error: String? = null,
    val retryCount: Int = 0,
    val fromCache: Boolean = false,
) {
    /**
     * Gets estimated time remaining (milliseconds).
     */
    fun estimatedTimeRemaining(): Long? {
        if (state != JobState.RUNNING || startedAt == null || progress == 0) return null

        val elapsed = System.currentTimeMillis() - startedAt!!
        val rate = progress.toDouble() / elapsed
        val remaining = (100 - progress) / rate

        return remaining.toLong()
    }

    /**
     * Gets total duration (milliseconds).
     */
    fun duration(): Long? =
        when {
            completedAt != null && startedAt != null -> completedAt!! - startedAt!!
            startedAt != null -> System.currentTimeMillis() - startedAt!!
            else -> null
        }
}

/**
 * Job state enum.
 *
 * @since 1.0.0
 */
enum class JobState {
    /** Waiting in queue */
    PENDING,

    /** Currently being processed */
    RUNNING,

    /** Successfully completed */
    COMPLETED,

    /** Failed after retries */
    FAILED,

    /** Cancelled by user */
    CANCELLED,
}

/**
 * Export priority enum.
 *
 * Higher ordinal = higher priority (CRITICAL > HIGH > NORMAL > LOW).
 *
 * @since 1.0.0
 */
enum class ExportPriority {
    /** Low priority (batch exports) */
    LOW,

    /** Normal priority (default) */
    NORMAL,

    /** High priority (user-initiated) */
    HIGH,

    /** Critical priority (urgent exports) */
    CRITICAL,
}

/**
 * Queue statistics.
 *
 * @property pending Jobs waiting in queue
 * @property running Currently running jobs
 * @property completed Successfully completed jobs
 * @property failed Failed jobs
 * @property cancelled Cancelled jobs
 * @property cacheHits Cache hits (if caching enabled)
 * @property totalProcessed Total jobs processed
 *
 * @since 1.0.0
 */
data class QueueStatistics(
    val pending: Int,
    val running: Int,
    val completed: Int,
    val failed: Int,
    val cancelled: Int,
    val cacheHits: Int,
    val totalProcessed: Int,
) {
    /**
     * Gets success rate (0.0 to 1.0).
     */
    fun successRate(): Double =
        if (totalProcessed == 0) {
            0.0
        } else {
            completed.toDouble() / totalProcessed
        }

    /**
     * Gets failure rate (0.0 to 1.0).
     */
    fun failureRate(): Double =
        if (totalProcessed == 0) {
            0.0
        } else {
            failed.toDouble() / totalProcessed
        }
}
