package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.FileService
import java.time.LocalDateTime
import java.util.UUID

@Component
class MirrorBackfill(
  private val courtDocumentRepository: CourtDocumentRepository,
  private val fileService: FileService,
  @Value("\${extraction.mirror.metadata-version:0}")
  private val metadataVersion: Int,
) : Backfill<UUID> {

  override val id = "mirror"
  override val concurrency = 8

  override fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<UUID> {
    val afterId = parseCursorUUID(cursor)
    val items = courtDocumentRepository.findUnmirroredAfter(afterId, metadataVersion, batchSize).map { it.id }
    val nextCursor = items.lastOrNull()?.toString() ?: cursor
    return BackfillBatch(items, nextCursor)
  }

  @Transactional
  override fun process(item: UUID) {
    val document = courtDocumentRepository.findById(item).get()
    val outcome = fileService.mirrorEnrichmentToDocumentStore(document)

    if (outcome.fullySuccessful) {
      document.metadataVersion = metadataVersion
      document.metadataUpdatedAt = LocalDateTime.now()
      courtDocumentRepository.save(document)
    } else {
      val cause = outcome.contentHashError ?: outcome.metadataError
        ?: IllegalStateException("Mirror failed with no captured cause")
      throw cause
    }
  }
}
