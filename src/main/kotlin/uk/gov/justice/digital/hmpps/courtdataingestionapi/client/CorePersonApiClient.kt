package uk.gov.justice.digital.hmpps.courtdataingestionapi.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.coreperson.CorePersonCanonicalRecord
import java.util.UUID

@Component
class CorePersonApiClient(@Qualifier("corePersonApiWebClient") private val webClient: WebClient) {

  fun getPersonByCommonPlatformId(defendantId: UUID): CorePersonCanonicalRecord = webClient
    .get()
    .uri("/person/commonplatform/$defendantId")
    .retrieve()
    .bodyToMono(CorePersonCanonicalRecord::class.java)
    .block()!!

  fun getPersonByPrisonerNumber(prisonerNumber: String): CorePersonCanonicalRecord {
    val getResponse = webClient
      .get()
      .uri("/person/prison/$prisonerNumber")
      .retrieve()

    log.info("GET response $getResponse")
    val response = getResponse
      .bodyToMono(CorePersonCanonicalRecord::class.java)
      .block()

    log.info("response $getResponse")

    return response!!
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
