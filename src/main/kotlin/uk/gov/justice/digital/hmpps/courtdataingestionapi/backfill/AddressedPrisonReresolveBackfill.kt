package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.slf4j.LoggerFactory
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
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
    val items = courtDocumentRepository.findByIdGreaterThanAndAddressedPrisonIsNullAndDeliveryMappingIdIsNullAndPrisonEmailAddressIsNotNullOrderById(
      afterId,
      Limit.of(batchSize),
    )
    return BackfillBatch(items, items.lastOrNull()?.id?.toString() ?: cursor)
  }

  @Transactional
  override fun process(item: CourtDocumentEntity) {
    val email = item.prisonEmailAddress ?: return

    val resolved = resolveEmailDestination.resolve(email) ?: return
    if (resolved.mappingId == null) return

    courtDocumentRepository.applyDeliveryResolution(
      id = item.id,
      addressedPrison = resolved.addressedPrison,
      deliveryMappingId = resolved.mappingId,
      deliverySource = resolved.destinationType,
    )

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
