package com.anuru.progressvault

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.anuru.progressvault.data.settings.SettingsRepository
import com.anuru.progressvault.work.Notifier
import com.anuru.progressvault.work.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.anuru.progressvault.work.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ProgressVaultApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var settingsRepository: SettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** On-demand WorkManager init so @HiltWorker workers get their dependencies injected. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
        WorkScheduler.schedule(this)
        // Fresh data on open (waits for network), and keep the periodic job's interval in sync with Settings.
        SyncWorker.syncSoon(this)
        appScope.launch {
            settingsRepository.settings.map { it.syncIntervalHours }.distinctUntilChanged()
                .collect { SyncWorker.schedulePeriodic(this@ProgressVaultApp, it) }
        }
        appScope.launch {
            settingsRepository.settings.map { it.reminderTime }.distinctUntilChanged()
                .collect { com.anuru.progressvault.work.ReminderWorker.schedule(this@ProgressVaultApp, it) }
        }
    }
}
