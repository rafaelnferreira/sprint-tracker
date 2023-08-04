package com.sprinttracker.services

import com.sprinttracker.models.Configuration
import com.sprinttracker.models.TimeEntry
import com.sprinttracker.models.WorkItem
import com.sprinttracker.models.WorkItemType
import org.azd.enums.WorkItemExpand
import org.azd.utils.AzDClientApi
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import org.azd.workitemtracking.types.WorkItem as ADOWorkItem


val ADOWorkItem.parentId: Int?
    get() = this.relations?.find { it.attributes.name == "Parent" }?.url?.let { url ->
        url.substring(url.lastIndexOf('/') + 1, url.length).toInt()
    }

internal class ADOTimeTrackingService(private val configuration: Configuration) {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    val webApi = AzDClientApi(
        configuration.servicesUrl,
        URLEncoder.encode(configuration.project, "utf-8").replace("+", "%20"),
        configuration.pat
    )

    fun findWorkItemsInIteration(): List<WorkItem> {
        val wit = webApi.workItemTrackingApi

        // analytics API can retrieve everything in a single query, future improvement.
        val query =
            "Select [System.Id] From WorkItems Where [System.IterationPath] = @currentIteration('[${configuration.project}]\\${configuration.team}') and [System.AssignedTo] = @Me and [System.State] != 'Closed'"
        logger.debug("Built query: {}", query)

        val result = wit.queryByWiql(configuration.team, query)

        val sprintWorkItems = result.workItems.toList()

        logger.debug("Found {} work items", sprintWorkItems.size)

        if (sprintWorkItems.isEmpty()) return emptyList()

        val adoItems = wit.getWorkItems(sprintWorkItems.map { it.id }.toIntArray(), WorkItemExpand.RELATIONS).workItems

        // getting the parents
        val parentIds = adoItems.toList()
            .map { it.parentId ?: 0 }
            .filter { it > 0 }
            .toIntArray()

        val parentItems =
            if (parentIds.isNotEmpty()) wit.getWorkItems(parentIds, WorkItemExpand.RELATIONS).workItems else emptyList()

        logger.debug("Found {} parent items", parentItems.size)

        // builds the graph in a very naive and slow way
        return createWorkItemGraph(listOf(adoItems + parentItems).flatten())
    }

    fun saveTimeEntry(entry: TimeEntry) {

        val remainingWork = entry.computeRemainingWork()
        val completedWork = entry.computeCompletedWork()
        logger.info(
            "Updating ADO work item: {}, with CompletedWork = {} and RemainingWork = {}",
            entry.workItem.id,
            completedWork,
            remainingWork
        )
        val fields = mutableMapOf<String, Any>(
            Pair("Microsoft.VSTS.Scheduling.RemainingWork", remainingWork),
            Pair("Microsoft.VSTS.Scheduling.CompletedWork", completedWork)
        )
            .takeIf { entry.closeWorkItem }?.apply { this["System.State"] = "Closed" }

        webApi.workItemTrackingApi.updateWorkItem(entry.workItem.id.toInt(), fields)
    }

    private fun createWorkItemGraph(
        adoWorkItems: List<ADOWorkItem>
    ): List<WorkItem> {

        val buffer: MutableMap<Int, WorkItem> = mutableMapOf()

        fun createGraph(currentADOWorkItems: List<ADOWorkItem>): List<WorkItem> {
            val adoWorkItem = currentADOWorkItems.firstOrNull() ?: return buffer.values.toList()

            val workItem = buffer.getOrDefault(
                adoWorkItem.id,
                crateWorkItem(adoWorkItem)
            )

            // process parent / child relationship
            val maybeParent = adoWorkItem.parentId?.let { parentId ->

                logger.debug("Processing parentId: {} for work item: {}", parentId, adoWorkItem.id)

                fun lazyCreateParent(): WorkItem {
                    val p = adoWorkItems.find { it.id == parentId }?.let { crateWorkItem(it) }
                    return p ?: WorkItem(
                        parentId.toLong(),
                        WorkItemType.EPIC,
                        "[Work item $parentId",
                        "New",
                        0.0,
                        0.0,
                        null,
                        null
                    )
                }

                val parentWorkItem = buffer.getOrDefault(
                    parentId,
                    lazyCreateParent()
                ) withChild workItem

                buffer[parentId] = parentWorkItem

                parentWorkItem
            }

            buffer[adoWorkItem.id] = if (maybeParent == null) workItem else workItem withParent maybeParent

            // extract to tail extension function
            return createGraph(currentADOWorkItems.subList(1, currentADOWorkItems.size))
        }

        val workItemsWithoutChildren = createGraph(adoWorkItems)

        // only first level supported: The children have a reference to their childless parent
        return workItemsWithoutChildren.map { workItem -> workItem withChildren workItemsWithoutChildren.filter { it.parent?.id == workItem.id } }
    }

    private fun ADOWorkItem.otherFieldAsDouble(name: String): Double =
        this.fields.otherFields[name].let { value -> if (value == null) 0.0 else value as Double }

    private fun crateWorkItem(adoWorkItem: ADOWorkItem): WorkItem {
        return WorkItem(
            adoWorkItem.id.toLong(),
            when (adoWorkItem.fields.systemWorkItemType) {
                "Bug" -> WorkItemType.BUG
                "Task" -> WorkItemType.TASK
                "Feature" -> WorkItemType.FEATURE
                else -> WorkItemType.USER_STORY
            },
            adoWorkItem.fields.systemTitle,
            adoWorkItem.fields.systemState,
            adoWorkItem.otherFieldAsDouble("Microsoft.VSTS.Scheduling.CompletedWork"),
            adoWorkItem.otherFieldAsDouble("Microsoft.VSTS.Scheduling.RemainingWork"),
            null,
            null
        )
    }

}