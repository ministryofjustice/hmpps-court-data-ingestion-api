package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.FileService
import java.time.LocalDateTime

@Component
class MirrorBackfill(
  private val courtDocumentRepository: CourtDocumentRepository,
  private val fileService: FileService,
) : Backfill<CourtDocumentEntity> {

  override val id = "mirror"
  override val concurrency = 8

  override fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<CourtDocumentEntity> {
    val afterId = parseCursor(cursor)
    val items = courtDocumentRepository.findUnmirroredAfter(afterId, batchSize)
    val nextCursor = items.lastOrNull()?.id?.toString() ?: cursor
    return BackfillBatch(items, nextCursor)
  }

  override fun process(item: CourtDocumentEntity) {
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
