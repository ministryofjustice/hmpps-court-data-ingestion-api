package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.step

import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.DestinationType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionEnricher
import uk.gov.justice.digital.hmpps.courtdataingestionapi.prisonemail.PrisonEmailNormaliser
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.EmailMapping
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.PrisonEmailMappingRepository

@Component
@Order(500)
class ResolveEmailDestination(
  private val prisonEmailMappingRepository: PrisonEmailMappingRepository,
) : IngestionEnricher {

  override fun enrich(context: IngestionContext): IngestionContext {
    val normalisedEmail = PrisonEmailNormaliser.normalise(context.prisonEmailAddress) ?: return context
    val mapping = prisonEmailMappingRepository.findMappingByEmail(normalisedEmail)

    return context.copy(
      addressedPrison = mapping?.prisonCode,
      destinationType = resolveDestinationType(normalisedEmail, mapping),
    )
  }

  private fun resolveDestinationType(normalisedEmail: String, mapping: EmailMapping?): DestinationType? =
    mapping?.sourceType?.let { runCatching { DestinationType.valueOf(it) }.getOrNull() }
      ?: when {
        normalisedEmail.endsWith("@geoamey.co.uk") -> DestinationType.PECS
        normalisedEmail.startsWith("pecs") && normalisedEmail.endsWith("@serco.com") -> DestinationType.PECS
        mapping?.prisonCode != null -> DestinationType.PRISON
        else -> null
      }
}
