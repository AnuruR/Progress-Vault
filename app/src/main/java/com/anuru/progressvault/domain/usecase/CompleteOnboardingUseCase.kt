package com.anuru.progressvault.domain.usecase

import com.anuru.progressvault.data.repository.AcademicsRepository
import com.anuru.progressvault.data.repository.SectionRepository
import com.anuru.progressvault.data.settings.SettingsRepository
import com.anuru.progressvault.domain.model.ModuleDraft
import java.time.LocalDate
import javax.inject.Inject

data class SemesterSetup(
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val modules: List<ModuleDraft>,
)

/** [target] null = leave the stored target alone (e.g. Academics, whose target comes from modules). */
data class TargetSetup(val sectionId: Long, val target: Double?, val includeInDial: Boolean)

/** Persists everything collected in onboarding, then marks onboarding as complete. */
class CompleteOnboardingUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val academicsRepository: AcademicsRepository,
    private val sectionRepository: SectionRepository,
) {
    suspend operator fun invoke(
        leetCodeUsername: String,
        gitHubUsername: String,
        semester: SemesterSetup?,
        targets: List<TargetSetup>,
    ) {
        settingsRepository.setUsernames(leetCodeUsername, gitHubUsername)
        semester?.let {
            academicsRepository.startSemester(it.name, it.startDate, it.endDate, it.modules)
        }
        targets.forEach {
            if (it.target != null) {
                sectionRepository.updateDefaultTarget(it.sectionId, it.target, it.includeInDial)
            } else {
                sectionRepository.setIncludeInDial(it.sectionId, it.includeInDial)
            }
        }
        settingsRepository.setOnboardingCompleted(true)
    }
}
