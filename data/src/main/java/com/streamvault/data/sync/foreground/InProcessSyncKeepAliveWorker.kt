package com.streamvault.data.sync.foreground

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** Keeps the process foreground while a screen-started sync (Settings, provider setup) runs in-process. */
class InProcessSyncKeepAliveWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        var finishedNormally = false
        try {
            runWithSyncForeground(SyncWorkKind.IN_PROCESS) {
                do {
                    awaitInProcessIdle()
                } while (!SyncCancellationRegistry.finishKeepAliveIfIdle())
                finishedNormally = true
                Result.success()
            }
        } finally {
            if (!finishedNormally) SyncCancellationRegistry.keepAliveStopped()
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "in-process-sync-keepalive"
        private const val IDLE_GRACE_MILLIS = 2_000L

        internal suspend fun awaitInProcessIdle(graceMillis: Long = IDLE_GRACE_MILLIS) {
            while (true) {
                SyncCancellationRegistry.active.first { active -> active.none { it.inProcess } }
                delay(graceMillis)
                if (!SyncCancellationRegistry.hasInProcessWork()) return
            }
        }

        fun install(context: Context) {
            val appContext = context.applicationContext ?: return
            SyncCancellationRegistry.installKeepAliveLauncher { enqueue(appContext) }
        }

        private fun enqueue(context: Context): Boolean = runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<InProcessSyncKeepAliveWorker>().build()
            )
            true
        }.getOrElse { error ->
            Log.w("InProcessSyncKeepAlive", "Keep-alive work unavailable: ${error.javaClass.simpleName}")
            false
        }
    }
}
