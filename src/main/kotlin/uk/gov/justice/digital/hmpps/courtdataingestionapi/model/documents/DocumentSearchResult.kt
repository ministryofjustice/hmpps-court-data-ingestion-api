package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents

data class DocumentSearchResult(
  val results: List<Document>,
  val totalResultsCount: Long,
)
