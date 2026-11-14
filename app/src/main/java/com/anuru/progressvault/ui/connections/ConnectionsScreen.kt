package com.anuru.progressvault.ui.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anuru.progressvault.R
import com.anuru.progressvault.data.db.entity.SyncSource
import com.anuru.progressvault.domain.sync.SyncOutcome
import com.anuru.progressvault.ui.components.StatusChip
import com.anuru.progressvault.ui.components.TerminalLabel
import com.anuru.progressvault.ui.components.VaultCard
import com.anuru.progressvault.ui.components.VaultPrimaryButton
import com.anuru.progressvault.ui.components.VaultTextField
import com.anuru.progressvault.ui.components.VaultTopBar
import com.anuru.progressvault.ui.theme.VaultColors

private val INTERVALS = listOf(1, 3, 6, 12, 24)
private const val TOKEN_HELP_URL = "https://github.com/settings/personal-access-tokens/new"

@Composable
fun ConnectionsScreen(onBack: () -> Unit, viewModel: ConnectionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form = state.form
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            val msg = when (e) {
                ConnectionsEvent.Saved -> resources.getString(R.string.connections_saved)
                is ConnectionsEvent.Synced -> {
                    val name = resources.getString(if (e.source == SyncSource.LEETCODE) R.string.connections_leetcode else R.string.connections_github)
                    val detail = when (val o = e.outcome) {
                        is SyncOutcome.Success -> resources.getString(R.string.sync_done)
                        SyncOutcome.Skipped -> null
                        is SyncOutcome.Failed -> resources.getString(o.error.messageRes())
                    }
                    detail?.let { resources.getString(R.string.sync_result, name, it) }
                }
            }
            msg?.let { snackbar.showSnackbar(it) }
        }
    }

    Scaffold(
        containerColor = VaultColors.Background,
        contentWindowInsets = WindowInsets(0),
        topBar = { VaultTopBar(title = stringResource(R.string.connections_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!form.loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.connections_intro), style = MaterialTheme.typography.bodyMedium, color = VaultColors.TextSecondary)

            VaultCard(Modifier.fillMaxWidth(), accent = VaultColors.Amber) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TerminalLabel(stringResource(R.string.connections_leetcode), color = VaultColors.Amber)
                    VaultTextField(
                        value = form.leetCode,
                        onValueChange = viewModel::onLeetCode,
                        label = stringResource(R.string.field_leetcode_username),
                        isError = !form.leetCodeValid,
                        supportingText = if (!form.leetCodeValid) stringResource(R.string.error_invalid_username) else null,
                    )
                    Text(stringResource(R.string.connections_leetcode_note), style = MaterialTheme.typography.bodySmall, color = VaultColors.TextMuted)
                    SyncStatusRow(
                        state = state.sync[SyncSource.LEETCODE],
                        configured = form.leetCode.isNotBlank(),
                        running = SyncSource.LEETCODE in state.running,
                        onSync = { viewModel.syncNow(SyncSource.LEETCODE) },
                    )
                }
            }

            VaultCard(Modifier.fillMaxWidth(), accent = VaultColors.Green) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TerminalLabel(stringResource(R.string.connections_github), color = VaultColors.Green)
                    VaultTextField(
                        value = form.gitHub,
                        onValueChange = viewModel::onGitHub,
                        label = stringResource(R.string.field_github_username),
                        isError = !form.gitHubValid,
                        supportingText = if (!form.gitHubValid) stringResource(R.string.error_invalid_username) else null,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.hasToken) {
                            StatusChip(stringResource(R.string.connections_token_saved), VaultColors.Green)
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = viewModel::removeToken) { Text(stringResource(R.string.action_remove_token)) }
                        } else {
                            StatusChip(stringResource(R.string.connections_token_none), VaultColors.TextMuted)
                        }
                    }
                    VaultTextField(
                        value = form.token,
                        onValueChange = viewModel::onToken,
                        label = stringResource(if (state.hasToken) R.string.connections_token_replace else R.string.connections_token_label),
                        leadingIcon = Icons.Outlined.Key,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Text(stringResource(R.string.connections_token_note), style = MaterialTheme.typography.bodySmall, color = VaultColors.TextMuted)
                    TextButton(onClick = { runCatching { uriHandler.openUri(TOKEN_HELP_URL) } }) {
                        Text(stringResource(R.string.connections_token_create))
                    }
                    SyncStatusRow(
                        state = state.sync[SyncSource.GITHUB],
                        configured = form.gitHub.isNotBlank(),
                        running = SyncSource.GITHUB in state.running,
                        onSync = { viewModel.syncNow(SyncSource.GITHUB) },
                    )
                }
            }

            VaultCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TerminalLabel(stringResource(R.string.connections_interval))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        INTERVALS.forEach { h ->
                            FilterChip(
                                selected = form.intervalHours == h,
                                onClick = { viewModel.onInterval(h) },
                                label = { Text(stringResource(R.string.interval_hours, h)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VaultColors.Cyan.copy(alpha = 0.18f),
                                    selectedLabelColor = VaultColors.Cyan,
                                ),
                            )
                        }
                    }
                    Text(stringResource(R.string.connections_interval_note), style = MaterialTheme.typography.bodySmall, color = VaultColors.TextMuted)
                }
            }

            Spacer(Modifier.height(4.dp))
            VaultPrimaryButton(
                text = stringResource(R.string.action_save_and_sync),
                onClick = viewModel::save,
                enabled = form.leetCodeValid && form.gitHubValid,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

