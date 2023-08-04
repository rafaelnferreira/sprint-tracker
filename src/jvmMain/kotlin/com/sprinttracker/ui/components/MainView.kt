package com.sprinttracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import com.sprinttracker.models.WorkItem
import com.sprinttracker.services.ConfigurationService
import com.sprinttracker.services.TimeEntrySaveSate
import com.sprinttracker.services.TimeTrackingServiceFacade
import com.sprinttracker.ui.platform.AppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import kotlin.concurrent.timerTask

enum class Pages {
    CONFIG, INPUT, CONFIRM
}

private val logger = LoggerFactory.getLogger("MainViewComponent")

@Composable
fun MainView(onNotification: (Notification) -> Unit, onExit: () -> Unit) {
    var configuration by remember { mutableStateOf(ConfigurationService.load()) }
    var page by remember { mutableStateOf(if (configuration.isValid()) Pages.INPUT else Pages.CONFIG) }

    var workItems by remember { mutableStateOf(emptyList<WorkItem>()) }
    var selectedWorkItems by remember { mutableStateOf(emptyList<WorkItem>()) }
    var loadingWorkItems by remember { mutableStateOf(true) }
    var timeEntrySaveSate by remember { mutableStateOf(TimeEntrySaveSate.COMPLETE) }
    var hoursLoggedToday by remember { mutableStateOf(0.0) }

    val coroutineScope = rememberCoroutineScope()

    val timeTrackingService = TimeTrackingServiceFacade(
        configuration,
        { loadingWorkItems = true },
        { loadedItems, totalHoursLoggedToday ->
            hoursLoggedToday = totalHoursLoggedToday
            workItems = loadedItems
            loadingWorkItems = false

            if (workItems.isNotEmpty()) {
                onNotification(
                    Notification(
                        "New work items!",
                        "Book time ahead of the daily meeting!",
                        Notification.Type.Info
                    )
                )
            }
        },
        {
            if (timeEntrySaveSate != TimeEntrySaveSate.COMPLETE && it == TimeEntrySaveSate.COMPLETE) {
                page = Pages.INPUT
            }
            timeEntrySaveSate = it
        },
    )

    LaunchedEffect(Unit) {
        scheduleRefresh(coroutineScope, timeTrackingService)

        timeTrackingService.findWorkItemsToEntryTime()
    }

    AppTheme {

        Surface(modifier = Modifier.fillMaxHeight()) {
            Box {
                Column() {
                    AppBar(
                        configurationComplete = configuration.isValid(),
                        onPageChange = { page = it },
                        onExit,
                        onRefresh = {
                            coroutineScope.launch {
                                timeTrackingService.refresh()
                            }
                        })

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(modifier = Modifier.padding(8.dp).verticalScroll(state = rememberScrollState())) {
                        when (page) {
                            Pages.CONFIG -> ConfigurationComponent(
                                configuration = configuration,
                                onSaveConfiguration = {
                                    configuration = ConfigurationService.save(it)
                                    coroutineScope.launch {
                                        timeTrackingService.refresh(configuration)
                                    }
                                })

                            Pages.INPUT -> TimeTrackingInput(workItems, loadingWorkItems, hoursLoggedToday) {
                                selectedWorkItems = it
                                page = Pages.CONFIRM
                            }

                            Pages.CONFIRM -> TimeTrackingConfirm(
                                selectedWorkItems,
                                timeEntrySaveSate == TimeEntrySaveSate.SAVING,
                                hoursLoggedToday)
                            {
                                coroutineScope.launch {
                                    timeTrackingService.saveTimeEntries(it)
                                }
                            }

                        }
                    }

                }
            }
        }


    }
}

private fun scheduleRefresh(
    coroutineScope: CoroutineScope,
    timeTrackingService: TimeTrackingServiceFacade
) {
    val twoHoursFromNow = LocalDateTime.now().plusHours(2)
    val nextRefresh = Date.from(twoHoursFromNow.toInstant(OffsetDateTime.now().offset))
    val twoHoursInMilliseconds = 2 * 60 * 60 * 1000

    logger.info(
        "Scheduled auto refresh to trigger at: {} - with a recurring period of {} milliseconds",
        nextRefresh,
        twoHoursInMilliseconds
    )

    Timer().schedule(
        timerTask {
            coroutineScope.launch { timeTrackingService.refresh() }
        },
        nextRefresh, twoHoursInMilliseconds.toLong()
    )
}