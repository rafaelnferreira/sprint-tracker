package com.sprinttracker.services

import com.sprinttracker.models.Persistable
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import java.io.File
import java.io.FileOutputStream

internal class CsvRepository<T>(tableName: String) where T : Persistable<T> {

    val DIRECTORY = File(System.getProperty("user.home"), ".timetracker")

    val FILE_NAME = "${tableName}.csv"

    init {
        if (!DIRECTORY.exists()) {
            DIRECTORY.mkdirs()
        }
    }

    fun loadList(mapper: (CSVRecord) -> T): List<T> {

        val file = File(DIRECTORY, FILE_NAME)
        if (file.exists()) {
            file.bufferedReader().use { reader ->
                return CSVFormat.Builder.create(CSVFormat.DEFAULT)
                    .apply { setIgnoreSurroundingSpaces(true) }
                    .build()
                    .parse(reader)
                    .map(mapper)
            }
        } else {
            return emptyList()
        }

    }

    fun load(mapper: (CSVRecord) -> T): T? = loadList(mapper).firstOrNull()

    fun save(input: T, converter: (T) -> Array<Any>, append: Boolean = false): T = save(listOf(input), converter, append).first()

    fun save(input: List<T>, converter: (T) -> Array<Any>, append: Boolean): List<T> {
        val file = File(DIRECTORY, FILE_NAME)
        FileOutputStream(file, append).bufferedWriter().use { writer ->
            CSVFormat.DEFAULT
                .print(writer)
                .apply {input.forEach { printRecord(*converter(it)) }
            }
        }
        return input
    }

}