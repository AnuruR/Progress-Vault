package com.anuru.progressvault.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.anuru.progressvault.R
import com.anuru.progressvault.domain.model.BuiltInSectionKey
import com.anuru.progressvault.ui.academics.AcademicsScreen
import com.anuru.progressvault.ui.academics.NewSemesterScreen
import com.anuru.progressvault.ui.academics.SemesterHistoryScreen
import com.anuru.progressvault.ui.courses.CourseEditScreen
import com.anuru.progressvault.ui.courses.CoursesScreen
import com.anuru.progressvault.ui.dashboard.DashboardScreen
import com.anuru.progressvault.ui.planner.PlannerScreen
import com.anuru.progressvault.ui.sections.ManageSectionsScreen
import com.anuru.progressvault.ui.sections.SectionDetailScreen
import com.anuru.progressvault.ui.sections.SectionEditScreen
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import com.anuru.progressvault.ui.main.DeepLink
import com.anuru.progressvault.ui.settings.SettingsScreen
import com.anuru.progressvault.ui.connections.ConnectionsScreen
import com.anuru.progressvault.ui.theme.VaultColors

@Composable
fun MainShell(
    deepLink: DeepLink? = null,
    onDeepLinkHandled: () -> Unit = {},
    askNotificationPermission: Boolean = false,
    onNotificationPermissionAsked: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    fun openTab(route: Any) = navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    // Notification taps. Recap links are consumed by the Vault screen (it scrolls to the card).
    LaunchedEffect(deepLink) {
        when (deepLink) {
            DeepLink.Dashboard -> { openTab(DashboardRoute); onDeepLinkHandled() }
            DeepLink.Planner -> { openTab(PlannerRoute); onDeepLinkHandled() }
            is DeepLink.Recap -> openTab(VaultRoute)
            null -> Unit
        }
    }

    // Android 13+: ask for POST_NOTIFICATIONS once, after onboarding. Settings offers it again later.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onNotificationPermissionAsked() }
    LaunchedEffect(askNotificationPermission) {
        if (askNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        containerColor = VaultColors.Background,
        bottomBar = {
            Box {
                NavigationBar(containerColor = VaultColors.Surface, tonalElevation = 0.dp) {
                    TopLevelDestination.entries.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any { it.hasRoute(dest.route::class) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.labelRes), style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = VaultColors.Cyan,
                                selectedTextColor = VaultColors.Cyan,
                                indicatorColor = VaultColors.Cyan.copy(alpha = 0.12f),
                                unselectedIconColor = VaultColors.TextMuted,
                                unselectedTextColor = VaultColors.TextMuted,
                            ),
                        )
                    }
                }
                HorizontalDivider(color = VaultColors.Border)
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = DashboardRoute,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable<DashboardRoute> {
                DashboardScreen(
                    onOpenSection = { section ->
                        navController.navigate(
                            when (section.builtInKey) {
                                BuiltInSectionKey.ACADEMICS -> AcademicsRoute
                                BuiltInSectionKey.COURSES -> CoursesRoute
                                else -> SectionDetailRoute(section.sectionId)
                            },
                        )
                    },
                    onManageSections = { navController.navigate(ManageSectionsRoute) },
                )
            }
            composable<SectionDetailRoute> {
                SectionDetailScreen(
                    onBack = navController::popBackStack,
                    onEdit = { navController.navigate(SectionEditRoute(it)) },
                    onConnections = { navController.navigate(ConnectionsRoute) },
                )
            }
            composable<SectionEditRoute> { SectionEditScreen(onDone = navController::popBackStack) }
            composable<ManageSectionsRoute> {
                ManageSectionsScreen(
                    onBack = navController::popBackStack,
                    onEdit = { navController.navigate(SectionEditRoute(it)) },
                    onAdd = { navController.navigate(SectionEditRoute()) },
                )
            }
            composable<AcademicsRoute> {
                AcademicsScreen(
                    onBack = navController::popBackStack,
                    onNewSemester = { navController.navigate(NewSemesterRoute) },
                    onOpenSemester = { navController.navigate(SemesterHistoryRoute(it)) },
                )
            }
            composable<NewSemesterRoute> { NewSemesterScreen(onDone = navController::popBackStack) }
            composable<SemesterHistoryRoute> { SemesterHistoryScreen(onBack = navController::popBackStack) }
            composable<CoursesRoute> {
                CoursesScreen(onBack = navController::popBackStack, onEdit = { navController.navigate(CourseEditRoute(it)) })
            }
            composable<CourseEditRoute> { CourseEditScreen(onDone = navController::popBackStack) }
            composable<PlannerRoute> { PlannerScreen() }
            composable<ConnectionsRoute> { ConnectionsScreen(onBack = navController::popBackStack) }
            composable<VaultRoute> {
                com.anuru.progressvault.ui.vault.VaultScreen(
                    recapLink = (deepLink as? DeepLink.Recap)?.month,
                    onRecapLinkHandled = onDeepLinkHandled,
                )
            }
            composable<SkillsRoute> { com.anuru.progressvault.ui.skills.SkillsScreen() }
            composable<SettingsRoute> {
                SettingsScreen(
                    onEditSection = { navController.navigate(SectionEditRoute(it)) },
                    onManageSections = { navController.navigate(ManageSectionsRoute) },
                    onConnections = { navController.navigate(ConnectionsRoute) },
                )
            }
        }
    }
}
