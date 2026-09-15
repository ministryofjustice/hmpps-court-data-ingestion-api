package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.step.ResolveEmailDestination
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository

@Component
class AddressedPrisonReresolveBackfill(
  private val courtDocumentRepository: CourtDocumentRepository,
  private val resolveEmailDestination: ResolveEmailDestination,
) : Backfill<CourtDocumentEntity> {

  override val id = "addressed-prison-reresolve"
  override val concurrency = 4

  override fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<CourtDocumentEntity> {
    val afterId = parseCursorUUID(cursor)
    val items = courtDocumentRepository.findUnaddressedAfter(afterId, batchSize)
    return BackfillBatch(items, items.lastOrNull()?.id?.toString() ?: cursor)
  }

  override fun process(item: CourtDocumentEntity) {
    val email = item.prisonEmailAddress ?: return

    val resolved = resolveEmailDestination.resolve(email) ?: return
    if (resolved.mappingId == null) return

    item.addressedPrison = resolved.addressedPrison
    item.deliveryMappingId = resolved.mappingId
    resolved.destinationType?.let { item.deliverySource = it }
    courtDocumentRepository.save(item)

    log.info(
      "Re-resolved court_document {} with mapping {} to prison {}",
      item.id,
      resolved.mappingId,
      resolved.addressedPrison,
    )
  }

  private companion object {
    private val log = LoggerFactory.getLogger(AddressedPrisonReresolveBackfill::class.java)
  }
}
