package uk.gov.justice.digital.hmpps.courtdataingestionapi.client

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.courtregister.CourtRegister
import java.util.UUID

@Component
class CourtRegisterApiClient(@Qualifier("courtRegisterApiWebClient") private val webClient: WebClient) {

  fun getCourtRegisterByHmctsId(hmctsCourtId: UUID): CourtRegister? = webClient
    .get()
    .uri("/courts/cp/{hmctsCourtId}", hmctsCourtId)
    .retrieve()
    .bodyToMono<CourtRegister>()
    .block()!!
}
