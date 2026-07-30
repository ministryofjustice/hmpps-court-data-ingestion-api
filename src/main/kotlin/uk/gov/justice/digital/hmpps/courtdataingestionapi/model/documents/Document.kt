package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents

import tools.jackson.databind.JsonNode
import java.time.LocalDateTime
import java.util.UUID

data class Document(
  val documentUuid: UUID,
  val documentType: DocumentApiType,
  val documentFilename: String,
  val filename: String,
  val fileExtension: String,
  val fileSize: Long,
  val fileHash: String,
  val fileContentHash: String? = null,
  val mimeType: String,
  val metadata: JsonNode,
  val createdTime: LocalDateTime,
  val createdByServiceName: String,
  val createdByUsername: String?,
  val duplicateOf: UUID? = null,
)

fun Document.isDisplayable(): Boolean = duplicateOf == null &&
  documentType.visibleInDocumentList &&
  metadata.get("status")?.asString() == DocumentMetadataStatus.ACTIVE.name
