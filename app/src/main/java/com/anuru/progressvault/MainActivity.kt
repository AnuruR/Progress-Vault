package com.anuru.progressvault

import android.content.Intent
import android.os.Bundle
import com.anuru.progressvault.ui.main.DeepLinks
import javax.inject.Inject
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anuru.progressvault.ui.main.AppState
import com.anuru.progressvault.ui.main.MainViewModel
import com.anuru.progressvault.ui.navigation.MainShell
import com.anuru.progressvault.ui.onboarding.OnboardingScreen
import com.anuru.progressvault.ui.theme.ProgressVaultTheme
import com.anuru.progressvault.ui.theme.VaultColors
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    @Inject lateinit var deepLinks: DeepLinks

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLinks.handle(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLinks.handle(intent)
        // Always-dark: force light system-bar icons regardless of system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(VaultColors.Background.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(VaultColors.Surface.toArgb()),
        )
        setContent {
            ProgressVaultTheme {
                val appState by viewModel.appState.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize().background(VaultColors.Background)) {
                    Crossfade(targetState = appState, label = "appState") { state ->
                        when (state) {
                            AppState.Loading -> Box(Modifier.fillMaxSize())
                            AppState.Onboarding -> OnboardingScreen()
                            AppState.Ready -> {
                                val link by deepLinks.pending.collectAsStateWithLifecycle()
                                val asked by viewModel.notificationPermissionAsked.collectAsStateWithLifecycle()
                                MainShell(
                                    deepLink = link,
                                    onDeepLinkHandled = deepLinks::consume,
                                    askNotificationPermission = asked == false,
                                    onNotificationPermissionAsked = viewModel::markNotificationPermissionAsked,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
