package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.step

import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.DeliveryCategory
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
  val addressedOrganisation: String?,
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
      addressedOrganisation = resolved.addressedOrganisation,
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
      addressedOrganisation = resolveAddressedOrganisation(normalisedEmail, mapping),
    )
  }

  private fun resolveAddressedOrganisation(normalisedEmail: String, mapping: EmailMapping?): String? = mapping?.categoryCode ?: when {
    normalisedEmail.endsWith("@geoamey.co.uk") -> DeliveryCategory.PECS
    normalisedEmail.startsWith("pecs") && normalisedEmail.endsWith("@serco.com") -> DeliveryCategory.PECS
    mapping?.prisonCode != null -> DeliveryCategory.PRISON
    else -> null
  }
}
