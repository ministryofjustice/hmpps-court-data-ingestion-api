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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches documents from document-management-api and backfills the corrected status metadata for documents uploaded in cdia
 */
@Component
class CdiaDocumentStatusBackfill(
  private val documentManagementApi: HmppsDocumentManagementApi,
) : Backfill<Document> {

  override val id = "cdia-document-status"

  private val attempted: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

  override fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<Document> {
    if (cursor.isEmpty()) attempted.clear()

    val searchRequest = DocumentSearchRequest(
      documentTypes = DocumentApiType.entries,
      metadata = JsonNodeFactory.instance.objectNode().apply {
        put("source", HmppsDocumentManagementApi.COURT_DATA_DOCUMENT_SOURCE)
        put("status", LEGACY_STATUS)
      },
      page = 0,
      pageSize = batchSize,
    )
    val results = try {
      documentManagementApi.search(searchRequest)
    } catch (e: Exception) {
      log.error("Error while searching document", e)
      return BackfillBatch(emptyList(), CURSOR)
    }

    val fresh = results.results.filterNot { attempted.contains(it.documentUuid) }
    if (fresh.isEmpty() && results.results.isNotEmpty()) {
      log.error(
        "Backfill {} made no progress: {} document(s) still {} and all already attempted",
        id,
        results.results.size,
        LEGACY_STATUS,
      )
      return BackfillBatch(emptyList(), CURSOR)
    }
    fresh.forEach { attempted.add(it.documentUuid) }
    return BackfillBatch(fresh, CURSOR)
  }

  override fun process(item: Document) {
    log.info("Backfilling document ${item.documentUuid} status to ${DocumentMetadataStatus.ACTIVE.name}, was $LEGACY_STATUS")
    documentManagementApi.mergeMetadata(item.documentUuid, metadata = mapOf("status" to DocumentMetadataStatus.ACTIVE.name))
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(CdiaDocumentStatusBackfill::class.java)
    private const val LEGACY_STATUS = "LIVE"
    private const val CURSOR = "0"
  }
}
