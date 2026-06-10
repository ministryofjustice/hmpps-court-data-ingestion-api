package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.FileService
import uk.gov.justice.digital.hmpps.courtdataingestionapi.util.Sha256
import java.time.LocalDateTime
import java.util.UUID

@Component
class HashBackfill(
  private val courtDocumentRepository: CourtDocumentRepository,
  private val documentManagementApi: HmppsDocumentManagementApi,
  private val fileService: FileService,
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
        extractTextHash(bytes)?.let { item.extractedTextSha256 = it }
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

  private fun extractTextHash(bytes: ByteArray): String? {
    if (!hasPdfHeader(bytes)) return null
    return runCatching {
      Loader.loadPDF(bytes).use { pdf ->
        val text = PDFTextStripper().getText(pdf)?.trim()
        if (text.isNullOrBlank()) null else Sha256.hex(text.toByteArray(Charsets.UTF_8))
      }
    }.getOrNull()
  }

  private fun hasPdfHeader(bytes: ByteArray): Boolean = bytes.size >= 4 &&
    bytes[0] == '%'.code.toByte() &&
    bytes[1] == 'P'.code.toByte() &&
    bytes[2] == 'D'.code.toByte() &&
    bytes[3] == 'F'.code.toByte()

  private fun parseCursor(cursor: String): UUID = if (cursor.isEmpty()) ZERO_UUID else UUID.fromString(cursor)

  companion object {
    private val ZERO_UUID = UUID(0L, 0L)
  }
}
