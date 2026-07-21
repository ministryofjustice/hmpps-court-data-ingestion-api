package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.node.JsonNodeFactory
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.Document
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentApiType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentMetadataStatus
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentSearchRequest

/**
 * Fetches documents from document-management-api and backfills the corrected status metadata for documents uploaded in cdia
 */
@Component
class CdiaDocumentStatusBackfill(
  private val documentManagementApi: HmppsDocumentManagementApi,
) : Backfill<Document> {

  override val id = "cdia-document-status"

  override fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<Document> {
    val page = parseCursorInt(cursor)
    val searchRequest = DocumentSearchRequest(
      documentTypes = DocumentApiType.entries,
      metadata = JsonNodeFactory.instance.objectNode().apply {
        put("source", HmppsDocumentManagementApi.COURT_DATA_DOCUMENT_SOURCE)
        put("status", "LIVE")
      },
      page = page,
      pageSize = batchSize,
    )
    val results = try {
      documentManagementApi.search(searchRequest)
    } catch (e: Exception) {
      log.error("Error while searching document", e)
      return BackfillBatch(emptyList(), "")
    }
    val nextCursor = page + 1
    return BackfillBatch(results.results, nextCursor.toString())
  }

  override fun process(item: Document) {
    log.info("Backfilling document ${item.documentUuid}")
    documentManagementApi.mergeMetadata(item.documentUuid, metadata = mapOf("status" to DocumentMetadataStatus.ACTIVE.name))
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(CdiaDocumentStatusBackfill::class.java)
  }
}
