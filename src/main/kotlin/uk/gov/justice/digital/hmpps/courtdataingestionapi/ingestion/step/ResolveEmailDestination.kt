package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.step

import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.DestinationType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionEnricher
import uk.gov.justice.digital.hmpps.courtdataingestionapi.prisonemail.PrisonEmailNormaliser
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.PrisonEmailMappingRepository

@Component
@Order(500)
class ResolveEmailDestination(
  private val prisonEmailMappingRepository: PrisonEmailMappingRepository,
) : IngestionEnricher {

  override fun enrich(context: IngestionContext): IngestionContext {
    val normalisedEmail = PrisonEmailNormaliser.normalise(context.prisonEmailAddress) ?: return context
    val mapping = prisonEmailMappingRepository.findMappingByEmail(normalisedEmail)

    val destinationType = mapping?.sourceType?.let { runCatching { DestinationType.valueOf(it) }.getOrNull() }
      ?: suffixBackstop(normalisedEmail, mapping?.prisonCode)

    return context.copy(
      addressedPrison = mapping?.prisonCode,
      destinationType = destinationType,
    )
  }

  private fun suffixBackstop(normalisedEmail: String, prisonCode: String?): DestinationType? = when {
    normalisedEmail.endsWith("@geoamey.co.uk") -> DestinationType.PECS
    normalisedEmail.startsWith("pecs") && normalisedEmail.endsWith("@serco.com") -> DestinationType.PECS
    prisonCode != null -> DestinationType.PRISON
    else -> null
  }
}
