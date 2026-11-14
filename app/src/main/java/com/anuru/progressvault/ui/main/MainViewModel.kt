package com.anuru.progressvault.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anuru.progressvault.data.db.seed.DatabaseSeeder
import com.anuru.progressvault.data.settings.SettingsRepository
import com.anuru.progressvault.domain.time.WeekCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AppState {
    data object Loading : AppState
    data object Onboarding : AppState
    data object Ready : AppState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    seeder: DatabaseSeeder,
    academicsRepository: com.anuru.progressvault.data.repository.AcademicsRepository,
    weekCalculator: WeekCalculator,
) : ViewModel() {

    init {
        viewModelScope.launch {
            seeder.ensureSeeded()
            settingsRepository.ensureFirstLaunchDate(weekCalculator.today())
            if (settingsRepository.consumeOnce("deadline_tasks_v1")) academicsRepository.backfillDeadlineTasks()
            if (settingsRepository.consumeOnce("github_green_v1")) seeder.applyGitHubGreen()
        }
    }

    /** null while loading, so the permission prompt never fires on a stale default. */
    val notificationPermissionAsked: StateFlow<Boolean?> = settingsRepository.notificationPermissionAsked
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun markNotificationPermissionAsked() {
        viewModelScope.launch { settingsRepository.setNotificationPermissionAsked() }
    }

    val appState: StateFlow<AppState> = settingsRepository.settings
        .map { if (it.onboardingCompleted) AppState.Ready else AppState.Onboarding }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppState.Loading)
}
