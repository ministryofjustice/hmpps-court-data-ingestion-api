package uk.gov.justice.digital.hmpps.courtdataingestionapi.client

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import uk.gov.justice.digital.hmpps.courtdataingestionapi.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.courtdataingestionapi.config.WebClientConfiguration.Companion.X_CORRELATION_ID_HEADER
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.CourthouseResponse
import uk.gov.justice.digital.hmpps.courtdataingestionapi.subscription.HmctsApiConfiguration
import java.util.UUID

@Component
class HmctsCourthouseApiClient(
  @Qualifier("hmctsCourthouseApiWebClient") private val webClient: WebClient,
  private val hmctsApiConfiguration: HmctsApiConfiguration,
  private val rateLimiter: HmctsRateLimiter,
) {
  fun getCourthouse(
    courthouseId: UUID,
  ): CourthouseResponse {
    rateLimiter.acquire()
    return webClient.get()
      .uri("/courthouses/$courthouseId")
      .header(SUBSCRIPTION_KEY_HEADER, hmctsApiConfiguration.courthouseKey)
      .header(X_CORRELATION_ID_HEADER, WebClientConfiguration.getCorrelationId().toString())
      .retrieve()
      .bodyToMono<CourthouseResponse>()
      .block()!!
  }

  companion object {
    const val SUBSCRIPTION_KEY_HEADER = "Ocp-Apim-Subscription-Key"
  }
}
