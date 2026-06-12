package com.clearguard.app.blocking

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.clearguard.app.PreferenceKeys
import com.clearguard.app.vpn.ClearGuardVpnService
import java.util.concurrent.TimeUnit

/**
 * Refreshes the downloaded blocklists in the background so protection stays current
 * without the user having to remember to tap "update". Scheduled by [sync]; WorkManager
 * defers runs until the device is online and the battery is not low.
 */
class BlocklistUpdateWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val result = BlocklistUpdater.updateNow(applicationContext)
        if (!result.success) {
            return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
        ClearGuardVpnService.reloadIfRunning(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "blocklist_auto_update"
        private const val MAX_RETRIES = 3

        /** Aligns the scheduled job with the auto-update preference. Safe to call repeatedly. */
        fun sync(context: Context) {
            val appContext = context.applicationContext
            val enabled = PreferenceKeys.prefs(appContext).getBoolean(
                PreferenceKeys.KEY_AUTO_UPDATE_ENABLED,
                PreferenceKeys.DEFAULT_AUTO_UPDATE_ENABLED
            )
            val workManager = WorkManager.getInstance(appContext)
            if (!enabled) {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }

            val request = PeriodicWorkRequestBuilder<BlocklistUpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
