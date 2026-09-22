package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.prisonersearch

data class PrisonerPage(
  val content: List<Prisoner> = emptyList(),
  val last: Boolean = true,
)
