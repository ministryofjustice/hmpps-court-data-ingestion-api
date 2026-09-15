package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.step

import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.DestinationType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionEnricher
import uk.gov.justice.digital.hmpps.courtdataingestionapi.prisonemail.PrisonEmailNormaliser
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.DeliveryCategoryRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.EmailMapping
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.PrisonEmailMappingRepository
import java.util.UUID

data class ResolvedDestination(
  val addressedPrison: String?,
  val mappingId: UUID?,
  val destinationType: DestinationType?,
)

@Component
@Order(500)
class ResolveEmailDestination(
  private val prisonEmailMappingRepository: PrisonEmailMappingRepository,
  private val categoryRepository: DeliveryCategoryRepository,
) : IngestionEnricher {

  override fun enrich(context: IngestionContext): IngestionContext {
    val resolved = resolve(context.prisonEmailAddress) ?: return context

    return context.copy(
      addressedPrison = resolved.addressedPrison,
      deliveryMappingId = resolved.mappingId,
      destinationType = resolved.destinationType,
    )
  }

  /**
   * Resolution without an [IngestionContext], so the re-resolution backfill can reuse exactly this
   * logic without having to build a context it has no other use for.
   */
  fun resolve(prisonEmailAddress: String?): ResolvedDestination? {
    val normalisedEmail = PrisonEmailNormaliser.normalise(prisonEmailAddress) ?: return null
    val mapping = prisonEmailMappingRepository.findMappingByEmail(normalisedEmail)
    val category = mapping?.categoryCode?.let { categoryRepository.findByCode(it) }

    // A category that carries no prison code (probation, youth custody) leaves addressedPrison
    // null: the document was not delivered to a prison, so there is nothing to record. It does not
    // affect who sees the document, which follows the person.
    val addressedPrison = if (category?.requiresPrisonCode == false) null else mapping?.prisonCode

    return ResolvedDestination(
      addressedPrison = addressedPrison,
      mappingId = mapping?.id,
      destinationType = resolveDestinationType(normalisedEmail, mapping),
    )
  }

  /**
   * A mapped category that has no matching [DestinationType] is a classification the delivery
   * source column cannot express, which is expected for anything other than PRISON and PECS.
   * It is logged rather than swallowed: before this was explicit, an unrecognised source type
   * fell silently through to the suffix rules below, so adding a new category appeared to work
   * while changing nothing.
   */
  private fun resolveDestinationType(normalisedEmail: String, mapping: EmailMapping?): DestinationType? {
    val declared = mapping?.categoryCode ?: mapping?.sourceType
    if (declared != null) {
      val mapped = runCatching { DestinationType.valueOf(declared) }.getOrNull()
      if (mapped != null) return mapped
      log.info(
        "Delivery address {} is classified as {}, which has no delivery source equivalent; leaving delivery_source null",
        normalisedEmail,
        declared,
      )
      return null
    }

    return when {
      normalisedEmail.endsWith("@geoamey.co.uk") -> DestinationType.PECS
      normalisedEmail.startsWith("pecs") && normalisedEmail.endsWith("@serco.com") -> DestinationType.PECS
      mapping?.prisonCode != null -> DestinationType.PRISON
      else -> null
    }
  }

  private companion object {
    private val log = LoggerFactory.getLogger(ResolveEmailDestination::class.java)
  }
}
