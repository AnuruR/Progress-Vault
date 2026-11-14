package com.anuru.progressvault.ui.main

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** Where a notification tap should land. */
sealed interface DeepLink {
    data object Dashboard : DeepLink
    data object Planner : DeepLink
    data class Recap(val month: YearMonth) : DeepLink
}

/** Hand-off from MainActivity (intent extras) to the navigation shell. */
@Singleton
class DeepLinks @Inject constructor() {
    private val _pending = MutableStateFlow<DeepLink?>(null)
    val pending: StateFlow<DeepLink?> = _pending.asStateFlow()

    fun handle(intent: Intent?) {
        val target = intent?.getStringExtra(EXTRA_TARGET) ?: return
        _pending.value = when {
            target == TARGET_PLANNER -> DeepLink.Planner
            target.startsWith(TARGET_RECAP) -> runCatching { DeepLink.Recap(YearMonth.parse(target.removePrefix(TARGET_RECAP))) }.getOrNull()
            else -> DeepLink.Dashboard
        }
        intent.removeExtra(EXTRA_TARGET)
    }

    fun consume() { _pending.value = null }

    companion object {
        const val EXTRA_TARGET = "pv_target"
        const val TARGET_DASHBOARD = "dashboard"
        const val TARGET_PLANNER = "planner"
        const val TARGET_RECAP = "recap:"
    }
}
