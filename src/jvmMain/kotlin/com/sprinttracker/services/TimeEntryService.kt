package com.sprinttracker.services

import com.sprinttracker.models.PersistableTimeEntry
import java.time.LocalDate

internal object TimeEntryService {

    private val csvRepository = CsvRepository<PersistableTimeEntry>("timeentries")

    // TODO ensure only the last entries of the file are read, otherwise it will blow over time
    private fun load(): List<PersistableTimeEntry> = csvRepository.loadList {
        PersistableTimeEntry(
            it[0].toDouble(),
            it[1].toLong(),
            it[2].toBoolean(),
            LocalDate.parse(it[3])
        )
    }

    fun findEntriesLoggedToday() = load().filter { it.date == LocalDate.now() }

    fun save(entry: PersistableTimeEntry) =
        csvRepository.save(entry, converter = { arrayOf(it.hours, it.workItemId, it.burn, it.date) }, append = true)

}