package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.ExtractedTextNormaliser
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.PdfTextExtractor
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.FileService
import uk.gov.justice.digital.hmpps.courtdataingestionapi.util.Sha256
import java.time.LocalDateTime

@Component
class HashBackfill(
  private val courtDocumentRepository: CourtDocumentRepository,
  private val documentManagementApi: HmppsDocumentManagementApi,
  private val fileService: FileService,
  private val pdfTextExtractor: PdfTextExtractor,
  private val normaliser: ExtractedTextNormaliser,
) : Backfill<CourtDocumentEntity> {

  override val id = "hash"
  override val concurrency = 4

  override fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<CourtDocumentEntity> {
    val afterId = parseCursor(cursor)
    val items = courtDocumentRepository.findUnhashedAfter(afterId, batchSize)
    val nextCursor = items.lastOrNull()?.id?.toString() ?: cursor
    return BackfillBatch(items, nextCursor)
  }

  override fun process(item: CourtDocumentEntity) {
    val needsFileHash = item.downloadedFileSha256.isNullOrBlank()
    val needsContentHash = item.extractedTextSha256.isNullOrBlank()

    if (needsFileHash || needsContentHash) {
      val bytes = documentManagementApi.downloadFile(item.prisonDocumentId)
      if (needsFileHash) {
        item.downloadedFileSha256 = Sha256.hex(bytes)
      }
      if (needsContentHash) {
        pdfTextExtractor.extractText(bytes)?.let { item.extractedTextSha256 = normaliser.getNormalisedHash(it) }
      }
      courtDocumentRepository.save(item)
    }

    val outcome = fileService.mirrorEnrichmentToDocumentStore(item)
    if (outcome.fullySuccessful) {
      item.mirroredToDocStoreAt = LocalDateTime.now()
      courtDocumentRepository.save(item)
    } else {
      val cause = outcome.contentHashError ?: outcome.metadataError
        ?: IllegalStateException("Mirror failed with no captured cause")
      throw cause
    }
  }
}
