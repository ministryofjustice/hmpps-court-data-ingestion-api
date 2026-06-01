package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.prisonersearch

data class Prisoner(
  val prisonerNumber: String,
  val prisonId: String? = "",
)
