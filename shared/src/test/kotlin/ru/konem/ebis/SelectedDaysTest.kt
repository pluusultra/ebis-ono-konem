package ru.konem.ebis

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.konem.ebis.data.Repeat
import ru.konem.ebis.data.TaskData
import ru.konem.ebis.data.TaskJson
import ru.konem.ebis.data.TaskOps
import java.time.DayOfWeek
import java.time.LocalDate

class SelectedDaysTest {
    private val monday = LocalDate.of(2026, 11, 23)
    private val days = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY)

    @Test fun followsSelectedDaysAfterEarlyAndLateCompletion() {
        val (tasks, id) = TaskOps.add(emptyList(), "Задача", "", monday, repeat = Repeat.SelectedDays, repeatDays = days)
        assertEquals(monday.plusDays(1), tasks.single().due)

        val afterEarlyCompletion = TaskOps.setDone(tasks, id, monday)
        val fridayTask = afterEarlyCompletion.single { !it.done }
        assertEquals(monday.plusDays(4), fridayTask.due)
        assertEquals(days, fridayTask.repeatDays)

        val nextTuesday = monday.plusDays(8)
        val afterLateCompletion = TaskOps.setDone(afterEarlyCompletion, fridayTask.id, nextTuesday)
        assertEquals(monday.plusDays(11), afterLateCompletion.single { !it.done }.due)
    }

    @Test fun editedDaysSurviveSyncFileAndOldWeeklyTasksStillLoad() {
        val (tasks, id) = TaskOps.add(emptyList(), "Задача", "", monday, repeat = Repeat.Weekly)
        val oldTasks = TaskJson.parse("""[{"id":$id,"title":"Задача","due":${monday.toEpochDay()},"repeat":"Weekly"}]""")
        assertEquals(Repeat.Weekly, oldTasks.single().repeat)
        assertEquals(emptySet<DayOfWeek>(), oldTasks.single().repeatDays)

        val edited = TaskOps.edit(tasks, id, "Задача", "", monday, null, Repeat.SelectedDays, false, days)
        assertEquals(monday.plusDays(1), edited.single().due)
        assertEquals(edited, TaskJson.parse(TaskJson.pretty(TaskData(edited))))
    }
}
