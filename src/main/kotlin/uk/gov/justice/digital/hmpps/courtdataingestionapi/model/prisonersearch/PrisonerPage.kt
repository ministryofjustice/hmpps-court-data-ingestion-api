package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.prisonersearch

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class PrisonerPage(
  val content: List<Prisoner> = emptyList(),
  val last: Boolean = true,
)
