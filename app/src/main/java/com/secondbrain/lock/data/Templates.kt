package com.secondbrain.lock.data

import com.secondbrain.lock.data.repo.NotesRepository
import com.secondbrain.lock.data.repo.PlannerRepository
import com.secondbrain.lock.data.repo.TasksRepository
import com.secondbrain.lock.network.dto.CreateNoteRequest
import com.secondbrain.lock.network.dto.CreatePlannerRoutineRequest
import java.time.LocalDate

enum class OnboardingTemplate(val label: String) {
    WRITER("Writer"),
    RESEARCHER("Researcher"),
    STUDENT("Student"),
    JUST_TASKS("Just tasks")
}

/**
 * Seeds real, deletable, immediately-visible starter content so no new user faces a blank
 * canvas (P20). [OnboardingTemplate.JUST_TASKS] deliberately seeds nothing — some users bounce
 * off any template, and that option must exist.
 *
 * Notes and tasks go through [NotesRepository.create]/[TasksRepository.create], which are both
 * already offline-safe (local-"local-" placeholder, queued via SyncQueue — P1/P1b). Routines are
 * NOT — [PlannerRepository.createRoutine] has no offline path today (a known, already-documented
 * gap from P1b's own TODO list, not something introduced here). A routine that fails to seed
 * offline is skipped silently rather than blocking the rest of the template — the user still
 * gets their tasks and notes, which matter more than the routine block.
 */
object Templates {
    suspend fun seed(template: OnboardingTemplate, studentSubjects: List<String> = emptyList()) {
        when (template) {
            OnboardingTemplate.WRITER -> seedWriter()
            OnboardingTemplate.RESEARCHER -> seedResearcher()
            OnboardingTemplate.STUDENT -> seedStudent(studentSubjects)
            OnboardingTemplate.JUST_TASKS -> Unit
        }
    }

    private suspend fun seedWriter() {
        NotesRepository.create(CreateNoteRequest(title = "Current draft", para = "project"))
        runCatching {
            PlannerRepository.createRoutine(
                CreatePlannerRoutineRequest(
                    title = "Writing block", startMin = 9 * 60, durationMin = 120,
                    category = "work", days = WEEKDAYS, source = "onboarding_template"
                )
            )
        }
        createTodayTask("Write 200 words", durationMin = 25, startMin = 9 * 60)
        createTodayTask("Read what you wrote yesterday", durationMin = 10, startMin = 9 * 60 + 25)
    }

    private suspend fun seedResearcher() {
        NotesRepository.create(CreateNoteRequest(title = "Current question", para = "project"))
        NotesRepository.create(CreateNoteRequest(title = "Sources to read", para = "area"))
        runCatching {
            PlannerRepository.createRoutine(
                CreatePlannerRoutineRequest(
                    title = "Reading block", startMin = 10 * 60, durationMin = 60,
                    category = "study", days = WEEKDAYS, source = "onboarding_template"
                )
            )
        }
        // Deliberately a WRITING task, not a reading one — research-as-avoidance is this
        // audience's specific failure mode (spec calls this out explicitly).
        createTodayTask("Write one paragraph about what you read", durationMin = 15, startMin = 11 * 60)
    }

    private suspend fun seedStudent(subjects: List<String>) {
        val areas = subjects.ifEmpty { listOf("Subject 1", "Subject 2", "Subject 3") }
        areas.forEach { subject -> NotesRepository.create(CreateNoteRequest(title = subject, para = "area")) }
        runCatching {
            PlannerRepository.createRoutine(
                CreatePlannerRoutineRequest(
                    title = "Study block", startMin = 19 * 60, durationMin = 60,
                    category = "study", days = ALL_DAYS, source = "onboarding_template"
                )
            )
        }
        createTodayTask("Review today's notes", durationMin = 20, startMin = 19 * 60)
        createTodayTask("Close the notes. Write what you remember.", durationMin = 10, startMin = 19 * 60 + 20)
    }

    private suspend fun createTodayTask(title: String, durationMin: Int, startMin: Int) {
        val today = LocalDate.now().toString()
        TasksRepository.create(title = title, dueDate = today).onSuccess { created ->
            TasksRepository.reschedule(created.id, startMin, durationMin)
        }
    }

    // 0=Mon..6=Sun, matching RoutineRepository.currentDayOfWeekIndex()'s convention (and the
    // server's `days int[]`) — NOT 0=Sunday.
    private val WEEKDAYS = listOf(0, 1, 2, 3, 4)
    private val ALL_DAYS = listOf(0, 1, 2, 3, 4, 5, 6)
}
