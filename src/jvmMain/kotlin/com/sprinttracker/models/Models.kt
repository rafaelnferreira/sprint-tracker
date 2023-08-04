package com.sprinttracker.models

import com.sprinttracker.services.TimeTrackingServiceFacade.Companion.EXPECTED_NUMBER_OF_TRACKED_HOURS_PER_DAY
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.max

// marker to allow persistence
interface Persistable<T>

data class Configuration(
    val servicesUrl: String = "",
    val project: String = "",
    val team: String = "",
    val pat: String = ""
) : Persistable<Configuration> {
    fun isValid(): Boolean = servicesUrl.isNotEmpty() && project.isNotEmpty() && team.isNotEmpty() && pat.isNotEmpty()
}

enum class WorkItemType {
    EPIC, FEATURE, USER_STORY, TASK, BUG
}

data class WorkItem(
    val id: Long,
    val type: WorkItemType,
    val title: String,
    val state: String,
    val completedWork: Double,
    val remainingWork: Double,
    val parent: WorkItem?,
    val children: List<WorkItem>?
) {
    infix fun withParent(parent: WorkItem) = WorkItem(
        this.id,
        this.type,
        this.title,
        this.state,
        this.completedWork,
        this.remainingWork,
        parent,
        this.children
    )

    infix fun withChild(child: WorkItem) = WorkItem(
        this.id,
        this.type,
        this.title,
        this.state,
        this.completedWork,
        this.remainingWork,
        this.parent,
        (this.children ?: emptyList()) + child
    )

    infix fun withChildren(children: List<WorkItem>) = WorkItem(
        this.id,
        this.type,
        this.title,
        this.state,
        this.remainingWork,
        this.completedWork,
        this.parent,
        children
    )

    infix fun isSame(other: WorkItem) = this.id == other.id

    val isPlaceholderOnly: Boolean
        get() = this.id < 1
}

data class TimeEntry(
    val hours: Double,
    val workItem: WorkItem,
    val burn: Boolean = true,
    val closeWorkItem: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val expectedNumOfHoursTrackedPerDay: Int = EXPECTED_NUMBER_OF_TRACKED_HOURS_PER_DAY,
) {
    fun toPersistable() = PersistableTimeEntry(hours, workItem.id, burn, date)

    fun computeRemainingWork(): Double = if (burn) max(workItem.remainingWork - hours, 0.0) else workItem.remainingWork

    fun computeCompletedWork(): Double =
        if (workItem.type == WorkItemType.TASK) workItem.completedWork + hours
        else workItem.completedWork + BigDecimal(hours / expectedNumOfHoursTrackedPerDay).setScale(2, RoundingMode.UP)
            .toDouble()
}

data class PersistableTimeEntry(val hours: Double, val workItemId: Long, val burn: Boolean, val date: LocalDate) :
    Persistable<PersistableTimeEntry>