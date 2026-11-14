package com.anuru.progressvault.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anuru.progressvault.data.backup.BackupOutcome
import com.anuru.progressvault.data.backup.BackupRepository
import com.anuru.progressvault.data.db.entity.SyncSource
import com.anuru.progressvault.data.db.entity.SyncStateEntity
import com.anuru.progressvault.data.repository.SectionRepository
import com.anuru.progressvault.data.repository.SyncRepository
import com.anuru.progressvault.data.repository.SyncStatusRepository
import com.anuru.progressvault.data.settings.SettingsRepository
import com.anuru.progressvault.domain.model.Section
import com.anuru.progressvault.domain.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

data class SettingsUiState(
    val isLoading: Boolean = true,
    val settings: UserSettings = UserSettings(),
    /** Sections that can sit in the dial (Skills is derived and never does). */
    val sections: List<Section> = emptyList(),
    val sync: Map<SyncSource, SyncStateEntity?> = emptyMap(),
    val syncing: Boolean = false,
    val backupBusy: Boolean = false,
) {
    // Academics' target comes from module hours, not defaultWeeklyTarget.
    private fun counts(s: Section) = s.includeInDial && (s.defaultWeeklyTarget > 0 || s.isAcademics)
    private val dialWeight: Double get() = sections.filter(::counts).sumOf { it.weight }

    /** Share of the dial this section controls, 0..1. */
    fun dialShare(s: Section): Double = if (!counts(s) || dialWeight == 0.0) 0.0 else s.weight / dialWeight
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val sectionRepository: SectionRepository,
    private val syncRepository: SyncRepository,
    private val backupRepository: BackupRepository,
    syncStatusRepository: SyncStatusRepository,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val _events = Channel<BackupOutcome>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        sectionRepository.observeAll(),
        syncStatusRepository.observeAll(),
        syncRepository.running,
        busy,
    ) { settings, sections, sync, running, b ->
        SettingsUiState(false, settings, sections.filterNot { it.isDerived }, sync, running.isNotEmpty(), b)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setInclude(s: Section, include: Boolean) = viewModelScope.launch { sectionRepository.setIncludeInDial(s.id, include) }
    fun changeWeight(s: Section, delta: Double) = viewModelScope.launch { sectionRepository.setWeight(s.id, s.weight + delta) }

    fun setReminderTime(t: LocalTime) = viewModelScope.launch { settingsRepository.setReminderTime(t) }
    fun setDaily(v: Boolean) = viewModelScope.launch { settingsRepository.setReminderToggles(daily = v) }
    fun setPace(v: Boolean) = viewModelScope.launch { settingsRepository.setReminderToggles(pace = v) }
    fun setWeekly(v: Boolean) = viewModelScope.launch { settingsRepository.setReminderToggles(weekly = v) }
    fun setMonthly(v: Boolean) = viewModelScope.launch { settingsRepository.setReminderToggles(monthly = v) }

    fun syncNow() = viewModelScope.launch { syncRepository.syncAll() }

    fun export(uri: Uri) = runBackup { backupRepository.export(uri) }
    fun import(uri: Uri) = runBackup { backupRepository.import(uri) }

    private fun runBackup(block: suspend () -> BackupOutcome) {
        if (busy.value) return
        viewModelScope.launch {
            busy.update { true }
            val result = try { block() } finally { busy.update { false } }
            _events.send(result)
        }
    }
}
