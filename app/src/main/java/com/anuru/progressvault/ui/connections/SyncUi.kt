package com.anuru.progressvault.ui.connections

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anuru.progressvault.R
import com.anuru.progressvault.data.db.entity.SyncStateEntity
import com.anuru.progressvault.domain.sync.SyncError
import com.anuru.progressvault.domain.sync.SyncOutcome
import com.anuru.progressvault.ui.theme.VaultColors

fun SyncError.messageRes(): Int = when (this) {
    SyncError.NOT_CONFIGURED -> R.string.sync_error_not_configured
    SyncError.USER_NOT_FOUND -> R.string.sync_error_user_not_found
    SyncError.UNAUTHORIZED -> R.string.sync_error_unauthorized
    SyncError.RATE_LIMITED -> R.string.sync_error_rate_limited
    SyncError.NETWORK -> R.string.sync_error_network
    SyncError.SERVER -> R.string.sync_error_server
    SyncError.BAD_RESPONSE -> R.string.sync_error_bad_response
}

/** True when the most recent attempt failed. */
val SyncStateEntity.isFailing: Boolean
    get() = lastError != null && (lastSuccessAt == null || (lastAttemptAt ?: lastSuccessAt) > lastSuccessAt)

@Composable
fun syncStatusText(state: SyncStateEntity?, configured: Boolean): Pair<String, Color> {
    if (!configured) return stringResource(R.string.sync_not_connected) to VaultColors.TextMuted
    if (state != null && state.isFailing) {
        val err = runCatching { SyncError.valueOf(state.lastError!!) }.getOrDefault(SyncError.BAD_RESPONSE)
        return stringResource(err.messageRes()) to VaultColors.Error
    }
    val last = state?.lastSuccessAt ?: return stringResource(R.string.chip_never_synced) to VaultColors.TextMuted
    return stringResource(
        R.string.chip_synced_ago,
        if (System.currentTimeMillis() - last.toEpochMilli() < DateUtils.MINUTE_IN_MILLIS) stringResource(R.string.sync_just_now)
            else DateUtils.getRelativeTimeSpanString(last.toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
    ) to VaultColors.Green
}

@Composable
fun syncOutcomeMessage(outcome: SyncOutcome): String = when (outcome) {
    is SyncOutcome.Success -> stringResource(R.string.sync_done)
    SyncOutcome.Skipped -> stringResource(R.string.sync_error_not_configured)
    is SyncOutcome.Failed -> stringResource(outcome.error.messageRes())
}

/** Status line + "Sync now" (spinner while running). */
@Composable
fun SyncStatusRow(
    state: SyncStateEntity?,
    configured: Boolean,
    running: Boolean,
    onSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (text, color) = syncStatusText(state, configured)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.weight(1f))
        if (running) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = VaultColors.Cyan)
            Spacer(Modifier.width(12.dp))
        } else if (configured) {
            TextButton(onClick = onSync) {
                Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_sync_now))
            }
        }
    }
}
