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

  fun resolve(prisonEmailAddress: String?): ResolvedDestination? {
    val normalisedEmail = PrisonEmailNormaliser.normalise(prisonEmailAddress) ?: return null
    val mapping = prisonEmailMappingRepository.findMappingByEmail(normalisedEmail)
    val category = mapping?.categoryCode?.let { categoryRepository.findById(it).orElse(null) }

    val addressedPrison = if (category?.requiresPrisonCode == false) null else mapping?.prisonCode

    return ResolvedDestination(
      addressedPrison = addressedPrison,
      mappingId = mapping?.id,
      destinationType = resolveDestinationType(normalisedEmail, mapping),
    )
  }

  private fun resolveDestinationType(normalisedEmail: String, emailMapping: EmailMapping?): DestinationType? {
    val addressClassification = emailMapping?.categoryCode ?: emailMapping?.sourceType
    if (addressClassification != null) {
      val mapped = runCatching { DestinationType.valueOf(addressClassification) }.getOrNull()
      if (mapped != null) return mapped
      log.info(
        "Delivery address {} is classified as {}, which has no delivery source equivalent; leaving delivery_source null",
        normalisedEmail,
        addressClassification,
      )
      return null
    }

    return when {
      normalisedEmail.endsWith("@geoamey.co.uk") -> DestinationType.PECS
      normalisedEmail.startsWith("pecs") && normalisedEmail.endsWith("@serco.com") -> DestinationType.PECS
      emailMapping?.prisonCode != null -> DestinationType.PRISON
      else -> null
    }
  }

  private companion object {
    private val log = LoggerFactory.getLogger(ResolveEmailDestination::class.java)
  }
}
