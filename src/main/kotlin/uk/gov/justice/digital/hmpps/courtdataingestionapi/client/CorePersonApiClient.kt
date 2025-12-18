package uk.gov.justice.digital.hmpps.courtdataingestionapi.client

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.coreperson.model.CanonicalRecord
import java.util.UUID

@Component
class CorePersonApiClient(@Qualifier("corePersonApiWebClient") private val webClient: WebClient) {

  fun getPerson(defendantId: UUID): CanonicalRecord = webClient
    .get()
    .uri("/person/commonplatform/$defendantId")
    .retrieve()
    .bodyToMono(CanonicalRecord::class.java)
    .block()!!
}
