package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.prisonemail.PrisonEmailNormaliser
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.PrisonEmailMappingRepository

@Component
@Order(500)
class IdentifyDestinationEnricher(
  private val prisonEmailMappingRepository: PrisonEmailMappingRepository,
) : IngestionEnricher {

  override fun enrich(context: IngestionContext): IngestionContext {
    val normalised = PrisonEmailNormaliser.normalise(context.prisonEmailAddress) ?: return context
    val prisonCode = prisonEmailMappingRepository.findPrisonCodeByEmail(normalised)

    val destinationType = when {
      normalised.endsWith("@geoamey.co.uk") -> DestinationType.PECS
      normalised.startsWith("pecs") && normalised.endsWith("@serco.com") -> DestinationType.PECS
      prisonCode != null -> DestinationType.PRISON
      else -> null
    }

    return context.copy(
      addressedPrison = prisonCode,
      destinationType = destinationType,
    )
  }
}
