package com.streamvault.data.sync.foreground

import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Marks coroutines owned by a WorkManager sync worker; unmarked lock holders are in-process work. */
internal class SyncWorkerContext : AbstractCoroutineContextElement(SyncWorkerContext) {
    companion object Key : CoroutineContext.Key<SyncWorkerContext>
}

data class ActiveProviderSync(val providerId: Long, val inProcess: Boolean)

/** Thrown into provider work that the user stopped from the sync notification. */
class UserCancelledSyncException : CancellationException("Sync stopped by the user")

/** Provider work holding the provider lock: cancellable from notifications, keeps in-process work alive. */
object SyncCancellationRegistry {
    private class Entry(val providerId: Long, val job: CompletableJob, val inProcess: Boolean)

    private val lock = Any()
    private val entries = LinkedHashSet<Entry>()
    private val mutableActive = MutableStateFlow<List<ActiveProviderSync>>(emptyList())
    private val cancelGeneration = AtomicLong(0)

    @Volatile
    private var keepAliveLauncher: (() -> Boolean)? = null
    private var keepAliveRunning = false

    val active: StateFlow<List<ActiveProviderSync>> = mutableActive.asStateFlow()

    fun installKeepAliveLauncher(launcher: () -> Boolean) {
        keepAliveLauncher = launcher
    }

    suspend fun <T> track(providerId: Long, block: suspend () -> T): T {
        val context = currentCoroutineContext()
        val entry = Entry(providerId, Job(context[Job]), inProcess = context[SyncWorkerContext] == null)
        register(entry)?.let { launchKeepAlive(it) }
        try {
            return withContext(entry.job) { block() }
        } finally {
            entry.job.complete()
            synchronized(lock) {
                entries.remove(entry)
                publish()
            }
        }
    }

    /** Cancels tracked work for [providerId]; returns whether anything was running. */
    fun cancel(providerId: Long): Boolean {
        val targets = synchronized(lock) { entries.filter { it.providerId == providerId } }
        cancelGeneration.incrementAndGet()
        targets.forEach { it.job.cancel(UserCancelledSyncException()) }
        return targets.isNotEmpty()
    }

    fun userCancelGeneration(): Long = cancelGeneration.get()

    fun hasInProcessWork(): Boolean = synchronized(lock) { entries.any { it.inProcess } }

    /** Called by an idle keep-alive worker; clears the running flag only when nothing is tracked. */
    fun finishKeepAliveIfIdle(): Boolean = synchronized(lock) {
        if (entries.none { it.inProcess }) {
            keepAliveRunning = false
            true
        } else {
            false
        }
    }

    fun keepAliveStopped() {
        synchronized(lock) { keepAliveRunning = false }
    }

    internal fun resetForTest() {
        synchronized(lock) {
            entries.clear()
            keepAliveRunning = false
            keepAliveLauncher = null
            publish()
        }
    }

    private fun register(entry: Entry): (() -> Boolean)? = synchronized(lock) {
        entries += entry
        publish()
        val launcher = keepAliveLauncher
        if (entry.inProcess && !keepAliveRunning && launcher != null) {
            keepAliveRunning = true
            launcher
        } else {
            null
        }
    }

    private fun launchKeepAlive(launcher: () -> Boolean) {
        val launched = runCatching(launcher).getOrDefault(false)
        if (!launched) keepAliveStopped()
    }

    private fun publish() {
        mutableActive.value = entries.map { ActiveProviderSync(it.providerId, it.inProcess) }
    }
}
