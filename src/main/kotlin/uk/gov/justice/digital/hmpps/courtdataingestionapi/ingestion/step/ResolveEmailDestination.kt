package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.prisonemail.PrisonEmailNormaliser
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.PrisonEmailMappingRepository

@Component
@Order(500)
class ResolveEmailDestination(
  private val prisonEmailMappingRepository: PrisonEmailMappingRepository,
) : IngestionEnricher {

  override fun enrich(context: IngestionContext): IngestionContext {
    val normalisedEmail = PrisonEmailNormaliser.normalise(context.prisonEmailAddress) ?: return context
    val prisonCode = prisonEmailMappingRepository.findPrisonCodeByEmail(normalisedEmail)

    return context.copy(
      addressedPrison = prisonCode,
      destinationType = classifyDestination(normalisedEmail, prisonCode),
    )
  }

  private fun classifyDestination(normalisedEmail: String, prisonCode: String?): DestinationType? = when {
    normalisedEmail.endsWith("@geoamey.co.uk") -> DestinationType.PECS
    normalisedEmail.startsWith("pecs") && normalisedEmail.endsWith("@serco.com") -> DestinationType.PECS
    prisonCode != null -> DestinationType.PRISON
    else -> null
  }
}
