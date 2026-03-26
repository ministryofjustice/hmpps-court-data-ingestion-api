package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.coreperson

data class CorePersonCanonicalIdentifiers(
  val prisonNumbers: List<String> = emptyList(),
  val defendantIds: List<String> = emptyList(),
)
