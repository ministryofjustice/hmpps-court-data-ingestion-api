package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.Document
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentApiType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentFacetSearchRequest
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentMetadataStatus
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.FilterOperator
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.MetadataFilter
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtDocumentService
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches documents from document-management-api and backfills the corrected isUnread metadata for documents uploaded in cdia
 */
@Component
class CdiaDocumentMetadataIsUnreadUpdateBackfill(
  private val documentManagementApi: HmppsDocumentManagementApi,
  private val courtDocumentService: CourtDocumentService,
) : Backfill<Document> {

  override val id = "cdia-document-is-unread"

  private val documentsAttempted: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

  override fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<Document> {
    if (cursor.isEmpty()) documentsAttempted.clear()

    val searchRequest = DocumentFacetSearchRequest(
      documentTypes = DocumentApiType.entries,
      metadataFilters = listOf(
        MetadataFilter("source", values = listOf(HmppsDocumentManagementApi.COURT_DATA_DOCUMENT_SOURCE)),
        MetadataFilter("status", values = listOf(DocumentMetadataStatus.ACTIVE.name)),
        MetadataFilter("prisonerId", FilterOperator.EXISTS),
        MetadataFilter("isUnread", values = listOf("true")),
      ),
      pageSize = batchSize,
    )
    val results = try {
      documentManagementApi.facetSearch(searchRequest)
    } catch (e: Exception) {
      log.error("Error while searching document", e)
      return BackfillBatch(emptyList(), CURSOR)
    }

    val documentsNotPreviouslyAttempted = results.results.filterNot { documentsAttempted.contains(it.documentUuid) }

    if (documentsNotPreviouslyAttempted.isEmpty() && results.results.isNotEmpty()) {
      log.error(
        "Backfill {} made no progress: {} document(s) still marked as New and all already attempted",
        id,
        results.results.size,
      )
      return BackfillBatch(emptyList(), CURSOR)
    }

    documentsNotPreviouslyAttempted.forEach { documentsAttempted.add(it.documentUuid) }
    return BackfillBatch(documentsNotPreviouslyAttempted, CURSOR)
  }

  override fun process(item: Document) {
    log.info("Backfilling document ${item.documentUuid} revisiting isUnread value, currently TRUE")
    val courtDocuments = courtDocumentService.getCourtDocumentsByPersonIdAndPrisonDocumentIds(
      item.metadata["prisonerId"].asString(),
      listOf(item.documentUuid),
    )

    if (courtDocuments.isEmpty()) {
      log.error("Backfill {} failed to get court document by prisonDocumentId: {} ", id, item.documentUuid)
      return
    }

    if (courtDocuments.first().isUnread) {
      return
    }

    log.info("Backfill {} updating document {} isUnread status to FALSE, was TRUE ", id, item.documentUuid)
    documentManagementApi.mergeMetadata(item.documentUuid, metadata = mapOf("isUnread" to false))
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(CdiaDocumentMetadataIsUnreadUpdateBackfill::class.java)
    private const val CURSOR = "0"
  }
}
