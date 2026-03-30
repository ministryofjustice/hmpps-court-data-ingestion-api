package uk.gov.justice.digital.hmpps.courtdataingestionapi.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.coreperson.CorePersonCanonicalRecord
import java.util.UUID

@Component
class CorePersonApiClient(@Qualifier("corePersonApiWebClient") private val webClient: WebClient) {

  fun getPersonByCommonPlatformId(defendantId: UUID): CorePersonCanonicalRecord {
    log.info("Getting core person record record for $defendantId")
    return webClient
      .get()
      .uri("/person/commonplatform/$defendantId")
      .retrieve()
      .bodyToMono(CorePersonCanonicalRecord::class.java)
      .block()!!
  }

  /**
   * Can return a null response if the person has been merged, resulting in a 301. However, this is unlikely when using a prisoner created event.
   */
  fun getPersonByPrisonerNumber(prisonerNumber: String): CorePersonCanonicalRecord? {
    log.info("Getting core person record record for $prisonerNumber")
    return webClient
      .get()
      .uri("/person/prison/$prisonerNumber")
      .retrieve()
      .bodyToMono(CorePersonCanonicalRecord::class.java)
      .block()
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
