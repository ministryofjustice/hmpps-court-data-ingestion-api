package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents

import tools.jackson.databind.JsonNode

data class DocumentSearchRequest(

  val documentTypes: List<DocumentApiType>?,
  val metadata: JsonNode? = null,

  val page: Int = 0,
  val pageSize: Int = 10,
)
