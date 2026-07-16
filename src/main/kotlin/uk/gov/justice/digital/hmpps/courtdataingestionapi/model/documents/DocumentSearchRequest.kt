package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents

data class DocumentSearchRequest(

  val documentTypes: List<DocumentApiType>?,

  // val metadata: JsonNode? TODO is this needed?
  val page: Int = 0,
  val pageSize: Int = 10,
)
