package com.sprinttracker.services

import com.sprinttracker.models.Configuration

object ConfigurationService {

    private val csvRepository = CsvRepository<Configuration>("configuration")

    fun save(configuration: Configuration): Configuration  = csvRepository.save(configuration, converter = { arrayOf( it.servicesUrl, it.project, it.team, it.pat) } )

    fun load(): Configuration =  csvRepository.load { Configuration(it[0], it[1], it[2], it[3]) } ?: Configuration()

}