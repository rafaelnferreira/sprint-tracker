package com.sprinttracker.services

import com.sprinttracker.models.Configuration
import com.sprinttracker.models.TimeEntry
import com.sprinttracker.models.WorkItem
import com.sprinttracker.models.WorkItemType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds

enum class TimeEntrySaveSate {
    SAVING, ERROR, COMPLETE
}

class TimeTrackingServiceFacade(
    private var configuration: Configuration,
    private val onWorkItemsLoading: () -> Unit,
    private val onWorkItemsLoaded: (workItems: List<WorkItem>, totalHoursLoggedToday: Double) -> Unit,
    private val onTimeEntriesSaving: (state: TimeEntrySaveSate) -> Unit
) {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    private val adoTimeTrackingService = ADOTimeTrackingService(configuration)

    suspend fun refresh(replaceConfig: Configuration? = null) = coroutineScope {
        logger.debug("Refresh triggered / new config? {}", replaceConfig != null)
        replaceConfig?.let { c -> configuration = c }
        findWorkItemsToEntryTime()
    }

    suspend fun findWorkItemsToEntryTime() = coroutineScope {

        if (configuration.isValid()) {
            launch {
                logger.debug("Find coroutine started")

                val entriesLoggedToday = TimeEntryService.findEntriesLoggedToday()
                val totalHoursLoggedToday = entriesLoggedToday.sumOf { it.hours }

                logger.info(
                    "Number of entries logged today: {}, with a total time of {} hours", entriesLoggedToday.size,
                    totalHoursLoggedToday
                )

                if (totalHoursLoggedToday >= EXPECTED_NUMBER_OF_TRACKED_HOURS_PER_DAY) {
                    logger.debug(
                        "Number of hours logged already exceed {}, lookup not happening",
                        EXPECTED_NUMBER_OF_TRACKED_HOURS_PER_DAY
                    )
                    onWorkItemsLoaded(emptyList(), totalHoursLoggedToday)
                } else {
                    val workItems = findWorkItemsInSprint()
                    onWorkItemsLoaded(workItems, totalHoursLoggedToday)
                }

            }
            onWorkItemsLoading()
        }

    }

    suspend fun saveTimeEntries(entries: List<TimeEntry>) = coroutineScope {
        logger.info("Saving {} time entries for date: {}", entries.size, LocalDate.now())
        launch {

            // just to allow a UI refresh
            delay(1.seconds)

            entries.forEach { entry ->
                // local persistence first - recoverable by deleting the entry in the file
                TimeEntryService.save(entry.toPersistable())

                // ADO - needs to be better handle when things go wrong
                adoTimeTrackingService.saveTimeEntry(entry)
            }

            // refresh and notify completion
            findWorkItemsToEntryTime()
            onTimeEntriesSaving(TimeEntrySaveSate.COMPLETE)
        }
        onTimeEntriesSaving(TimeEntrySaveSate.SAVING)
    }

    private fun findWorkItemsInSprint(): List<WorkItem> = adoTimeTrackingService.findWorkItemsInIteration()
        .filter { it.type == WorkItemType.USER_STORY || it.type == WorkItemType.BUG }
        .map { maybeCreateFakeChild(it) }
        .sortedBy { it.type }

    private fun maybeCreateFakeChild(it: WorkItem) =
        if (it.children?.isNotEmpty() == true || (it.type == WorkItemType.USER_STORY && !ALLOW_TIME_ENTRY_AT_USER_STORY_WITHOUT_TASK)) it
        else it withChildren listOf(
            WorkItem(
                it.id * -1,
                WorkItemType.TASK,
                "(No task - time will be captured in the parent)",
                "New",
                0.0,
                0.0,
                it,
                emptyList()
            )
        )

    companion object {
        const val EXPECTED_NUMBER_OF_TRACKED_HOURS_PER_DAY = 6
        const val ALLOW_TIME_ENTRY_AT_USER_STORY_WITHOUT_TASK = false
    }

}