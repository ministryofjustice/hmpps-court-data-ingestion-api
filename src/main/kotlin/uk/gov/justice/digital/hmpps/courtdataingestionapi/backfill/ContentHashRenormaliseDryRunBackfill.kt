package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.ExtractedTextNormaliser
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.PdfTextExtractor
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository

@Component
class ContentHashRenormaliseDryRunBackfill(
  private val courtDocumentRepository: CourtDocumentRepository,
  private val documentManagementApi: HmppsDocumentManagementApi,
  private val pdfTextExtractor: PdfTextExtractor,
  private val normaliser: ExtractedTextNormaliser,
) : Backfill<CourtDocumentEntity> {

  override val id = "content-hash-renormalise-dry-run"
  override val concurrency = 4

  override fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<CourtDocumentEntity> {
    val afterId = parseCursor(cursor)
    val items = courtDocumentRepository.findHashedAfter(afterId, batchSize)
    val nextCursor = items.lastOrNull()?.id?.toString() ?: cursor
    return BackfillBatch(items, nextCursor)
  }

  override fun process(item: CourtDocumentEntity) {
    val currentHash = item.extractedTextSha256?.takeIf { it.isNotBlank() } ?: return
    val bytes = documentManagementApi.downloadFile(item.prisonDocumentId)
    val text = pdfTextExtractor.extractText(bytes) ?: return
    val newHash = normaliser.normalisedHash(text)

    if (newHash != currentHash) {
      log.info(
        "Dry run: document {} (court_document {}) would change content hash {} -> {}",
        item.prisonDocumentId,
        item.id,
        currentHash,
        newHash,
      )
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(ContentHashRenormaliseDryRunBackfill::class.java)
  }
}
