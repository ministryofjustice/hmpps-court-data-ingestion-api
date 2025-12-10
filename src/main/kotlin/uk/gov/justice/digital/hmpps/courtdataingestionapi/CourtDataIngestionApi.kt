package uk.gov.justice.digital.hmpps.courtdataingestionapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CourtDataIngestionApi

fun main(args: Array<String>) {
  runApplication<CourtDataIngestionApi>(*args)
}
