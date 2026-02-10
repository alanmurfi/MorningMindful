package com.morningmindful.util

import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace
import com.morningmindful.BuildConfig

/**
 * Centralized performance tracing using Firebase Performance Monitoring.
 *
 * Custom traces for key operations:
 * - App startup
 * - Database operations (save entry, load entries)
 * - Image loading/processing
 * - Backup operations
 * - Blocking check
 */
object PerformanceTraces {

    private val firebasePerf: FirebasePerformance by lazy {
        FirebasePerformance.getInstance().also {
            // Disable performance collection in debug builds
            it.isPerformanceCollectionEnabled = !BuildConfig.DEBUG
        }
    }

    // Trace names
    private const val TRACE_APP_STARTUP = "app_startup"
    private const val TRACE_SAVE_ENTRY = "save_journal_entry"
    private const val TRACE_LOAD_ENTRIES = "load_journal_entries"
    private const val TRACE_LOAD_ENTRY = "load_single_entry"
    private const val TRACE_SAVE_IMAGE = "save_image"
    private const val TRACE_LOAD_IMAGES = "load_images"
    private const val TRACE_CREATE_BACKUP = "create_backup"
    private const val TRACE_RESTORE_BACKUP = "restore_backup"
    private const val TRACE_BLOCKING_CHECK = "blocking_check"
    private const val TRACE_ENCRYPTION_INIT = "encryption_init"

    // Active traces (for stop tracking)
    private val activeTraces = mutableMapOf<String, Trace>()

    /**
     * Start a trace for app startup.
     * Call this at the beginning of Application.onCreate().
     */
    fun startAppStartup(): Trace {
        return startTrace(TRACE_APP_STARTUP)
    }

    /**
     * Start a trace for saving a journal entry.
     */
    fun startSaveEntry(): Trace {
        return startTrace(TRACE_SAVE_ENTRY)
    }

    /**
     * Start a trace for loading all journal entries.
     */
    fun startLoadEntries(): Trace {
        return startTrace(TRACE_LOAD_ENTRIES)
    }

    /**
     * Start a trace for loading a single entry.
     */
    fun startLoadEntry(): Trace {
        return startTrace(TRACE_LOAD_ENTRY)
    }

    /**
     * Start a trace for saving an image.
     */
    fun startSaveImage(): Trace {
        return startTrace(TRACE_SAVE_IMAGE)
    }

    /**
     * Start a trace for loading images.
     */
    fun startLoadImages(): Trace {
        return startTrace(TRACE_LOAD_IMAGES)
    }

    /**
     * Start a trace for creating a backup.
     */
    fun startCreateBackup(): Trace {
        return startTrace(TRACE_CREATE_BACKUP)
    }

    /**
     * Start a trace for restoring a backup.
     */
    fun startRestoreBackup(): Trace {
        return startTrace(TRACE_RESTORE_BACKUP)
    }

    /**
     * Start a trace for blocking check operations.
     */
    fun startBlockingCheck(): Trace {
        return startTrace(TRACE_BLOCKING_CHECK)
    }

    /**
     * Start a trace for encryption initialization.
     */
    fun startEncryptionInit(): Trace {
        return startTrace(TRACE_ENCRYPTION_INIT)
    }

    /**
     * Start a custom trace with the given name.
     */
    fun startTrace(traceName: String): Trace {
        val trace = firebasePerf.newTrace(traceName)
        trace.start()
        activeTraces[traceName] = trace
        return trace
    }

    /**
     * Stop a trace by name.
     */
    fun stopTrace(traceName: String) {
        activeTraces.remove(traceName)?.stop()
    }

    /**
     * Execute a block with performance tracing.
     * Automatically stops the trace when the block completes.
     */
    inline fun <T> trace(traceName: String, block: (Trace) -> T): T {
        val trace = startTrace(traceName)
        return try {
            block(trace)
        } finally {
            trace.stop()
        }
    }

    /**
     * Execute a suspend block with performance tracing.
     */
    suspend inline fun <T> traceSuspend(traceName: String, crossinline block: suspend (Trace) -> T): T {
        val trace = startTrace(traceName)
        return try {
            block(trace)
        } finally {
            trace.stop()
        }
    }
}

/**
 * Extension function to add metrics to a trace.
 */
fun Trace.addMetric(name: String, value: Long) {
    putMetric(name, value)
}

/**
 * Extension function to add attributes to a trace.
 */
fun Trace.addAttribute(name: String, value: String) {
    putAttribute(name, value)
}

/**
 * Extension function to increment a metric.
 */
fun Trace.incrementMetric(name: String, incrementBy: Long = 1) {
    incrementMetric(name, incrementBy)
}
