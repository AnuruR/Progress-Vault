package com.anuru.progressvault.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.anuru.progressvault.R
import kotlinx.serialization.Serializable

@Serializable data object DashboardRoute
@Serializable data object PlannerRoute
@Serializable data object VaultRoute
@Serializable data object SkillsRoute
@Serializable data object SettingsRoute

/** Bottom-navigation destinations. */
enum class TopLevelDestination(
    val route: Any,
    val icon: ImageVector,
    @param:StringRes val labelRes: Int,
) {
    DASHBOARD(DashboardRoute, Icons.Outlined.DataUsage, R.string.nav_dashboard),
    PLANNER(PlannerRoute, Icons.Outlined.CalendarViewWeek, R.string.nav_planner),
    VAULT(VaultRoute, Icons.Outlined.Inventory2, R.string.nav_vault),
    SKILLS(SkillsRoute, Icons.Outlined.Psychology, R.string.nav_skills),
    SETTINGS(SettingsRoute, Icons.Outlined.Settings, R.string.nav_settings),
}

// Detail destinations (pushed on top of the tab content).
@Serializable data class SectionDetailRoute(val sectionId: Long)
/** sectionId = 0 creates a new custom section. */
@Serializable data class SectionEditRoute(val sectionId: Long = 0)
@Serializable data object ManageSectionsRoute
@Serializable data object AcademicsRoute
@Serializable data object NewSemesterRoute
@Serializable data class SemesterHistoryRoute(val semesterId: Long)
@Serializable data object CoursesRoute
/** courseId = 0 creates a new course. */
@Serializable data class CourseEditRoute(val courseId: Long = 0)
@Serializable data object ConnectionsRoute
