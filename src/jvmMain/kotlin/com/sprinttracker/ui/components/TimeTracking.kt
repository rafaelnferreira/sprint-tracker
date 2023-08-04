package com.sprinttracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.sprinttracker.models.TimeEntry
import com.sprinttracker.models.WorkItem
import com.sprinttracker.models.WorkItemType
import com.sprinttracker.services.TimeTrackingServiceFacade.Companion.EXPECTED_NUMBER_OF_TRACKED_HOURS_PER_DAY
import com.sprinttracker.ui.platform.*
import com.sprinttracker.utils.replaceElement
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.max

data class SelectableWorkItem(val selected: Boolean, val workItem: WorkItem)

private const val HOUR_INCREMENT = 0.5
private const val ZERO = 0.0

data class TimeEntryInput(val hours: String, val workItem: WorkItem, val burn: Boolean = true, val closeWorkItem: Boolean = false) {

    fun toTimeEntry(): TimeEntry = TimeEntry(hoursAsDouble() ?: ZERO, workItem, burn, closeWorkItem)

    fun isError(): Boolean = (hoursAsDouble() ?: ZERO) < HOUR_INCREMENT

    fun plusOneHour(): String {
        val maybeHours = hoursAsDouble()?.let { h -> (h + HOUR_INCREMENT).toString() }
        return maybeHours ?: hours
    }

    fun minusOneHour(): String {
        val maybeHours = hoursAsDouble()?.let { h -> max((h - HOUR_INCREMENT), HOUR_INCREMENT).toString() }
        return maybeHours ?: hours
    }

    private fun hoursAsDouble(): Double? {
        return try {
            BigDecimal(hours).setScale(2, RoundingMode.UP).toDouble()
        } catch (e: Exception) {
            null
        }
    }

}

private val logger = LoggerFactory.getLogger("TimeTrackingComponent")

@Composable
fun TimeTrackingInput(
    workItems: List<WorkItem>,
    loadingWorkItems: Boolean,
    hoursLoggedToday: Double,
    onWorkItemsSelected: (List<WorkItem>) -> Unit
) {

    logger.debug("TimeTrackingInputState: isPerformingTask/{}, numOfWorkItems/{}", loadingWorkItems, workItems.size)

    var selectedWorkItems by remember { mutableStateOf(listOf<SelectableWorkItem>()) }

    if (loadingWorkItems) {

        Column {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            Text(
                text = "Loading work items and time entries...",
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp)
            )
        }

    } else if (workItems.isNotEmpty()) {
        Column {

            HoursLoggedDisplay(hoursLoggedToday)

            Column(modifier = Modifier.height(400.dp).verticalScroll(state = rememberScrollState())) {
                workItems.forEach {
                    WorkItemCard(it) { selected ->
                        val selectedIds = selected.map { s -> s.workItem.id }
                        selectedWorkItems =
                            selectedWorkItems.filter { w -> !selectedIds.contains(w.workItem.id) } + selected.filter { w -> w.selected }
                    }
                }
            }

            Divider()

            Row(modifier = Modifier.align(Alignment.End).padding(16.dp)) {

                Text(
                    "${selectedWorkItems.size} items selected.",
                    modifier = Modifier.align(Alignment.CenterVertically).padding(horizontal = 16.dp)
                )

                Button(modifier = Modifier.align(Alignment.CenterVertically),
                    enabled = selectedWorkItems.isNotEmpty(),
                    onClick = { onWorkItemsSelected(selectedWorkItems.map { if (it.workItem.isPlaceholderOnly) it.workItem.parent!! else it.workItem }) }) {
                    Text("Next")
                }

            }
        }
    } else {

        Column {

            HoursLoggedDisplay(hoursLoggedToday)

            Text(
                text = "All good - There's no work left to do here today!",
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp)
            )
        }
    }
}

private fun List<WorkItem>.suggestedHours(alreadyLogged: Double): Double {
    return (EXPECTED_NUMBER_OF_TRACKED_HOURS_PER_DAY - alreadyLogged) / this.size.toBigDecimal().setScale(2, RoundingMode.UP).toDouble()
}

@Composable
fun TimeTrackingConfirm(workItems: List<WorkItem>, isSaving: Boolean, hoursLoggedToday: Double, onSave: (entries: List<TimeEntry>) -> Unit) {
    val hoursPerTask = workItems.suggestedHours(hoursLoggedToday)

    var timeEntries by remember { mutableStateOf(workItems.map { TimeEntryInput(hoursPerTask.toString(), it) }) }

    logger.debug("TimeTrackingConfirmState: isSaving/{}, numOfWorkItems/{}", isSaving, workItems.size)

    Column {

        HoursLoggedDisplay(hoursLoggedToday)

        Column(modifier = Modifier.height(400.dp).verticalScroll(state = rememberScrollState())) {
            timeEntries.forEach {
                WorkItemTimeEntry(it) { hours, burn, closeWorkItem ->
                    timeEntries = timeEntries.replaceElement(
                        predicate = { elem -> it.workItem isSame elem.workItem },
                        supplier = { TimeEntryInput(hours, it.workItem, burn, closeWorkItem) }
                    )
                }
            }
        }

        Divider()

        Row(modifier = Modifier.align(Alignment.End).padding(16.dp)) {

            if (isSaving) {
                CircularProgressIndicator(strokeWidth = 8.dp)
            }

            Button(modifier = Modifier.align(Alignment.CenterVertically),
                enabled = !isSaving && timeEntries.isNotEmpty() && timeEntries.all { !it.isError() },
                onClick = { onSave(timeEntries.map { it.toTimeEntry() }) }) {
                Text("Save")
            }

        }
    }
}


private fun WorkItem.iconColorPair(): Pair<String, Color> {
    return when (this.type) {

        WorkItemType.TASK -> Pair(Res.drawable.ic_task, yellow400)
        WorkItemType.BUG -> Pair(
            Res.drawable.ic_bug,
            red400
        )

        else -> Pair(Res.drawable.ic_user_story, blue400)
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun WorkItemCard(item: WorkItem, onSelectionChange: (workItems: List<SelectableWorkItem>) -> Unit) {

    var selectedWorkItems by remember {
        mutableStateOf(item.children?.map { SelectableWorkItem(false, it) })
    }

    val (icon, tint) = item.iconColorPair()

    Column {

        ListItem(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = { Text(item.title) },
            icon = { Icon(painterResource(icon), contentDescription = null, tint = tint) },
        )

        selectedWorkItems?.forEach {
            WorkItemSelection(it) { selected ->
                selectedWorkItems = selectedWorkItems!!.replaceElement(
                    predicate = { w -> w.workItem isSame it.workItem },
                    supplier = { SelectableWorkItem(selected, it.workItem) }
                )
                onSelectionChange(selectedWorkItems!!)
            }
        }

        if (selectedWorkItems?.isEmpty() == true) {
            ListItem(
                modifier = Modifier.padding(horizontal = 32.dp),
                text = { Text("(Create a task first to log time)") }
            )
        }

        Divider()
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun WorkItemSelection(item: SelectableWorkItem, selectionChange: (selected: Boolean) -> Unit) {

    ListItem(
        modifier = Modifier.padding(horizontal = 16.dp),
        secondaryText = { Text(item.workItem.state) },
        text = { Text(item.workItem.title) },
        icon = {
            Icon(
                painterResource(Res.drawable.ic_task),
                contentDescription = null,
                tint = yellow400,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        },
        trailing = {
            Checkbox(
                checked = item.selected,
                modifier = Modifier.padding(8.dp),
                onCheckedChange = { selectionChange(!item.selected) }
            )
        }
    )

}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun WorkItemTimeEntry(timeEntry: TimeEntryInput, onEntryChanged: (hours: String, burn: Boolean, closeWorkItem: Boolean) -> Unit) {
    val workItem = timeEntry.workItem
    val (icon, tint) = workItem.iconColorPair()
    ListItem(
        modifier = Modifier.padding(horizontal = 16.dp),
        secondaryText = { workItem.parent?.title?.let { Text(it) } },
        text = { Text(workItem.title) },
        icon = {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = tint,
            )
        },
        trailing = {
            TimeEntryCapture(timeEntry, onEntryChanged)
        }
    )
}

@Composable
private fun TimeEntryCapture(timeEntry: TimeEntryInput, onEntryChanged: (hours: String, burn: Boolean, closeWorkItem: Boolean) -> Unit) {

    val inputValue = TextFieldValue(timeEntry.hours)

    Row {
        TextField(
            value = inputValue,
            onValueChange = { onEntryChanged(it.text, timeEntry.burn, timeEntry.closeWorkItem)},
            modifier = Modifier.width(96.dp).padding(8.dp).align(Alignment.CenterVertically),
            singleLine = true,
            label = { Text("Hours") },
            isError = timeEntry.isError()
        )
        TextButton(onClick = {onEntryChanged(timeEntry.plusOneHour(), timeEntry.burn, timeEntry.closeWorkItem)}, modifier = Modifier.align(Alignment.CenterVertically)) {
            Icon(
                painterResource(Res.drawable.ic_plus),
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        TextButton(onClick = {onEntryChanged(timeEntry.minusOneHour(), timeEntry.burn, timeEntry.closeWorkItem)}, modifier = Modifier.align(Alignment.CenterVertically)) {
            Icon(
                painterResource(Res.drawable.ic_minus),
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        Icon(
            painterResource(Res.drawable.ic_burn),
            tint = orange200,
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterVertically)
        )
        Checkbox(
            modifier = Modifier.align(Alignment.CenterVertically),
            checked = timeEntry.burn,
            onCheckedChange = { onEntryChanged(timeEntry.hours, !timeEntry.burn, timeEntry.closeWorkItem) }
        )
        Text("Close", modifier = Modifier.align(Alignment.CenterVertically))
        Checkbox(
            modifier = Modifier.align(Alignment.CenterVertically),
            checked = timeEntry.closeWorkItem,
            onCheckedChange = { onEntryChanged(timeEntry.hours, timeEntry.burn, !timeEntry.closeWorkItem) }
        )
    }
}

@Composable
private fun HoursLoggedDisplay(hoursLoggedToday: Double) {
        Text("Time logged today: $hoursLoggedToday hours / Expected: $EXPECTED_NUMBER_OF_TRACKED_HOURS_PER_DAY hours", modifier = Modifier.padding(16.dp))
}