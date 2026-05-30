package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.prisonemail.PrisonEmailNormaliser
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.PrisonEmailMappingRepository

@Component
@Order(100)
class AddressedPrisonEnricher(
  private val prisonEmailMappingRepository: PrisonEmailMappingRepository,
) : IngestionEnricher {

  private val log = LoggerFactory.getLogger(this::class.java)

  override fun enrich(context: IngestionContext): IngestionContext {
    val normalised = PrisonEmailNormaliser.normalise(context.prisonEmailAddress)
      ?: return context

    return runCatching {
      val prisonCode =
        prisonEmailMappingRepository.findPrisonCodeByEmail(normalised)

      context.copy(addressedPrison = prisonCode)
    }.getOrElse {
      log.warn("Failed to resolve addressed_prison, continuing without enrichment", it)
      context
    }
  }
}
