package com.anuru.progressvault.ui.courses

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.anuru.progressvault.data.db.dao.SectionDao
import com.anuru.progressvault.data.db.entity.CourseEntity
import com.anuru.progressvault.data.db.entity.CoursePlatform
import com.anuru.progressvault.data.db.entity.CourseStatus
import com.anuru.progressvault.data.repository.CourseRepository
import com.anuru.progressvault.data.repository.CourseWithProgress
import com.anuru.progressvault.data.repository.LogRepository
import com.anuru.progressvault.data.repository.ProgressRepository
import com.anuru.progressvault.domain.model.BuiltInSectionKey
import com.anuru.progressvault.domain.progress.SectionProgress
import com.anuru.progressvault.domain.time.WeekCalculator
import com.anuru.progressvault.ui.navigation.CourseEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class LessonLogged(val logId: Long, val courseTitle: String)

data class CoursesUiState(
    val isLoading: Boolean = true,
    val courses: List<CourseWithProgress> = emptyList(),
    val section: SectionProgress? = null,
)

@HiltViewModel
class CoursesViewModel @Inject constructor(
    private val repository: CourseRepository,
    private val logRepository: LogRepository,
    private val sectionDao: SectionDao,
    progressRepository: ProgressRepository,
    weekCalculator: WeekCalculator,
) : ViewModel() {
    private val _logged = Channel<LessonLogged>(Channel.BUFFERED)
    /** Emits after a lesson is logged, so the UI can offer Undo. */
    val logged: Flow<LessonLogged> = _logged.receiveAsFlow()

    val state: StateFlow<CoursesUiState> = combine(
        repository.observeAll(),
        progressRepository.observeWeek(weekCalculator.currentWeek()),
    ) { courses, week ->
        CoursesUiState(
            isLoading = false,
            courses = courses.sortedWith(compareBy<CourseWithProgress> { it.course.status.ordinal }.thenByDescending { it.course.createdAt }),
            section = week.sections.firstOrNull { it.builtInKey == BuiltInSectionKey.COURSES },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoursesUiState())

    fun undo(logId: Long) {
        viewModelScope.launch { logRepository.undo(logId) }
    }

    fun logLesson(course: CourseEntity) {
        viewModelScope.launch {
            val section = sectionDao.getByBuiltInKey(BuiltInSectionKey.COURSES.key) ?: return@launch
            val id = logRepository.log(section.id, 1.0, courseId = course.id)
            _logged.send(LessonLogged(id, course.title))
        }
    }
}

data class CourseEditState(
    val isLoading: Boolean = true,
    val id: Long = 0,
    val title: String = "",
    val platform: CoursePlatform = CoursePlatform.COURSERA,
    val url: String = "",
    val totalLessonsText: String = "",
    val status: CourseStatus = CourseStatus.IN_PROGRESS,
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val startDate: LocalDate? = null,
    val finishDate: LocalDate? = null,
    val certificateUrl: String = "",
    val showErrors: Boolean = false,
    val done: Boolean = false,
    val original: CourseEntity? = null,
) {
    val totalLessons: Int? get() = totalLessonsText.trim().toIntOrNull()
    val totalLessonsError: Boolean get() = totalLessonsText.isNotBlank() && (totalLessons == null || totalLessons!! <= 0)
    val isValid: Boolean get() = title.isNotBlank() && !totalLessonsError
}

@HiltViewModel
class CourseEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CourseRepository,
    weekCalculator: WeekCalculator,
) : ViewModel() {
    private val courseId = savedStateHandle.toRoute<CourseEditRoute>().courseId
    private val _state = MutableStateFlow(CourseEditState(startDate = weekCalculator.today()))
    val state: StateFlow<CourseEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val c = if (courseId == 0L) null else repository.get(courseId)
            _state.update { s ->
                if (c == null) s.copy(isLoading = false)
                else s.copy(
                    isLoading = false, id = c.id, title = c.title, platform = c.platform, url = c.url.orEmpty(),
                    totalLessonsText = c.totalLessons?.toString().orEmpty(), status = c.status, tags = c.skillTags,
                    startDate = c.startDate, finishDate = c.finishDate, certificateUrl = c.certificateUrl.orEmpty(), original = c,
                )
            }
        }
    }

    fun update(t: (CourseEditState) -> CourseEditState) = _state.update(t)

    /** Adds the typed tag(s); commas split multiple tags. */
    fun commitTag() = _state.update { s ->
        val new = s.tagInput.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        s.copy(tags = (s.tags + new).distinctBy { it.lowercase() }, tagInput = "")
    }

    fun removeTag(tag: String) = _state.update { it.copy(tags = it.tags - tag) }

    fun save() {
        commitTag()
        val s = _state.value
        if (!s.isValid) { _state.update { it.copy(showErrors = true) }; return }
        viewModelScope.launch {
            val base = s.original ?: CourseEntity(title = "", platform = s.platform, createdAt = java.time.Instant.EPOCH)
            repository.save(
                base.copy(
                    title = s.title, platform = s.platform, url = s.url, totalLessons = s.totalLessons, status = s.status,
                    skillTags = s.tags, startDate = s.startDate, finishDate = s.finishDate, certificateUrl = s.certificateUrl,
                ),
            )
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val c = _state.value.original ?: return
        viewModelScope.launch { repository.delete(c); _state.update { it.copy(done = true) } }
    }
}
