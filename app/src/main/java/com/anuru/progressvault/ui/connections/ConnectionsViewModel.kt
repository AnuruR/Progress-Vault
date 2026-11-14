package com.anuru.progressvault.ui.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anuru.progressvault.data.db.entity.SyncSource
import com.anuru.progressvault.data.db.entity.SyncStateEntity
import com.anuru.progressvault.data.repository.SyncRepository
import com.anuru.progressvault.data.repository.SyncStatusRepository
import com.anuru.progressvault.data.security.TokenStore
import com.anuru.progressvault.data.settings.SettingsRepository
import com.anuru.progressvault.domain.sync.SyncOutcome
import com.anuru.progressvault.domain.sync.Usernames
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectionsForm(
    val leetCode: String = "",
    val gitHub: String = "",
    val token: String = "",
    val intervalHours: Int = 6,
    val loaded: Boolean = false,
) {
    val leetCodeValid get() = Usernames.isValidLeetCode(leetCode)
    val gitHubValid get() = Usernames.isValidGitHub(gitHub)
}

data class ConnectionsUiState(
    val form: ConnectionsForm = ConnectionsForm(),
    val hasToken: Boolean = false,
    val sync: Map<SyncSource, SyncStateEntity?> = emptyMap(),
    val running: Set<SyncSource> = emptySet(),
)

sealed interface ConnectionsEvent {
    data object Saved : ConnectionsEvent
    data class Synced(val source: SyncSource, val outcome: SyncOutcome) : ConnectionsEvent
}

@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val tokenStore: TokenStore,
    private val syncRepository: SyncRepository,
    syncStatusRepository: SyncStatusRepository,
) : ViewModel() {
    private val form = MutableStateFlow(ConnectionsForm())
    private val _events = Channel<ConnectionsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val s = settings.settings.first()
            form.value = ConnectionsForm(s.leetCodeUsername, s.gitHubUsername, "", s.syncIntervalHours, loaded = true)
        }
    }

    val state: StateFlow<ConnectionsUiState> = combine(
        form, tokenStore.hasGitHubToken, syncStatusRepository.observeAll(), syncRepository.running,
    ) { f, hasToken, sync, running -> ConnectionsUiState(f, hasToken, sync, running) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionsUiState())

    fun onLeetCode(v: String) = form.update { it.copy(leetCode = v) }
    fun onGitHub(v: String) = form.update { it.copy(gitHub = v) }
    fun onToken(v: String) = form.update { it.copy(token = v) }
    fun onInterval(h: Int) = form.update { it.copy(intervalHours = h) }

    fun removeToken() = tokenStore.setGitHubToken(null)

    /** Saves, then syncs right away so the user sees whether the usernames work. */
    fun save() {
        val f = form.value
        if (!f.leetCodeValid || !f.gitHubValid) return
        viewModelScope.launch {
            settings.setUsernames(f.leetCode, f.gitHub)
            settings.setSyncIntervalHours(f.intervalHours)
            if (f.token.isNotBlank()) tokenStore.setGitHubToken(f.token)
            form.update { it.copy(token = "") }
            _events.send(ConnectionsEvent.Saved)
            syncNow(SyncSource.LEETCODE)
            syncNow(SyncSource.GITHUB)
        }
    }

    fun syncNow(source: SyncSource) {
        viewModelScope.launch {
            val outcome = when (source) {
                SyncSource.LEETCODE -> syncRepository.syncLeetCode()
                SyncSource.GITHUB -> syncRepository.syncGitHub()
            }
            _events.send(ConnectionsEvent.Synced(source, outcome))
        }
    }
}
