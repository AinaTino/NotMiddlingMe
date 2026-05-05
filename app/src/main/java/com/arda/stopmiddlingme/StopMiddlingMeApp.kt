package com.arda.stopmiddlingme

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class StopMiddlingMeApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: dagger.Lazy<HiltWorkerFactory>

    override fun onCreate() {
        super.onCreate()
        // On garde onCreate au strict minimum pour éviter les ANR "AppBindData" (what=110).
        // Le réchauffement de la DB et le scheduling WorkManager sont déplacés 
        // vers le DashboardViewModel ou effectués à la demande.
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory.get())
            .build()
}
