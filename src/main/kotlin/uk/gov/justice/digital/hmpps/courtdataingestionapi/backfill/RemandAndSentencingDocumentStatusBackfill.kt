package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.Document
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentApiType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentMetadataStatus
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentSearchRequest

/**
 * Fetches documents from document-management-api and backfills the missing status metadata for documents uploaded in ras
 */
@Component
class RemandAndSentencingDocumentStatusBackfill(
  private val documentManagementApi: HmppsDocumentManagementApi,
) : Backfill<Document> {

  override val id = "remand-and-sentencing-document-status"

  override fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<Document> {
    val page = parseCursorInt(cursor)
    val searchRequest = DocumentSearchRequest(
      documentTypes = DocumentApiType.entries,
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
    val metadata = item.metadata
    val source = metadata["source"]
    val status = metadata["status"]

    val documentIsFromCdia = source == HmppsDocumentManagementApi.COURT_DATA_DOCUMENT_SOURCE
    val documentHasCorrectStatus = statusMap.values.map { it.name }.contains(status)

    if (!documentIsFromCdia && !documentHasCorrectStatus) {
      log.info("Backfilling document ${item.documentUuid}")

      val newStatus = statusMap[status ?: "Active"] ?: DocumentMetadataStatus.ACTIVE
      documentManagementApi.mergeMetadata(item.documentUuid, metadata = mapOf("status" to newStatus.name))
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(RemandAndSentencingDocumentStatusBackfill::class.java)
    private val statusMap = mapOf(
      "Active" to DocumentMetadataStatus.ACTIVE,
      "Awaiting" to DocumentMetadataStatus.AWAITING,
      "Deleted" to DocumentMetadataStatus.DELETED,
    )
  }
}
