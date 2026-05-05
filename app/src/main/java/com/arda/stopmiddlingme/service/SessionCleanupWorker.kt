package com.arda.stopmiddlingme.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.arda.stopmiddlingme.data.repository.SessionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SessionCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: SessionRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result =
        try {
            repository.closeExpiredSessions()
            repository.purgeExpiredSignals()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }

    companion object {
        private const val WORK_NAME = "session_cleanup_worker"

        /**
         * Planifie le nettoyage périodique des sessions expirées.
         * Note: Ce worker dépend de Hilt pour l'injection des dépendances.
         * Si vous rencontrez des erreurs de Factory, assurez-vous que:
         * 1. StopMiddlingMeApp étend Application et est annoté @HiltAndroidApp
         * 2. Hilt est correctement configuré dans build.gradle.kts
         * 3. Vous pouvez également désactiver ce worker si son instanciation pose problème
         */
        fun schedule(context: Context) {
            try {
                val request = PeriodicWorkRequestBuilder<SessionCleanupWorker>(15, TimeUnit.MINUTES)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
                    .addTag(WORK_NAME)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            } catch (e: Exception) {
                e.printStackTrace()
                // Silencieusement échouer — ce n'est pas un bug critique
            }
        }

        /**
         * Désactive le worker si nécessaire (pour déboguer les erreurs Hilt)
         */
        fun cancelSchedule(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_NAME)
        }
    }
}
