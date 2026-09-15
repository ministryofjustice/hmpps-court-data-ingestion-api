package uk.gov.justice.digital.hmpps.courtdataingestionapi.client

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.util.UriBuilder
import uk.gov.justice.digital.hmpps.courtdataingestionapi.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.courtdataingestionapi.config.WebClientConfiguration.Companion.X_CORRELATION_ID_HEADER
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.DefendantDetails
import uk.gov.justice.digital.hmpps.courtdataingestionapi.subscription.HmctsApiConfiguration
import java.util.UUID

@Component
class HmctsCourtDefendantApiClient(
  @Qualifier("hmctsCourtDefendantApiWebClient") private val webClient: WebClient,
  private val hmctsApiConfiguration: HmctsApiConfiguration,
  private val rateLimiter: HmctsRateLimiter,
) {

  fun getDefendants(
    caseUrn: String,
    masterDefendantId: UUID? = null,
    defendantId: UUID? = null,
  ): List<DefendantDetails> = try {
    rateLimiter.acquire()
    webClient.get()
      .uri { builder: UriBuilder ->
        builder.path("/defendants/cases/{caseURN}")
        masterDefendantId?.let { builder.queryParam("masterDefendantId", it) }
        defendantId?.let { builder.queryParam("defendantId", it) }
        builder.build(caseUrn)
      }
      .apply {
        if (hmctsApiConfiguration.courtDefendantKey.isNotBlank()) {
          header(HmctsApiConfiguration.SUBSCRIPTION_KEY_HEADER, hmctsApiConfiguration.courtDefendantKey)
        }
      }
      .header(X_CORRELATION_ID_HEADER, WebClientConfiguration.getCorrelationId().toString())
      .retrieve()
      .bodyToMono<List<DefendantDetails>>()
      .block() ?: emptyList()
  } catch (e: WebClientResponseException) {
    if (HttpStatus.NOT_FOUND.isSameCodeAs(e.statusCode)) emptyList() else throw e
  }
}
