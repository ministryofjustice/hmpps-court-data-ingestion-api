package uk.gov.justice.digital.hmpps.courtdataingestionapi.client

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import uk.gov.justice.digital.hmpps.courtdataingestionapi.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.courtdataingestionapi.config.WebClientConfiguration.Companion.X_CORRELATION_ID_HEADER
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.CourtScheduleResponse
import uk.gov.justice.digital.hmpps.courtdataingestionapi.subscription.HmctsApiConfiguration

@Component
class HmctsCourtScheduleApiClient(@Qualifier("hmctsCourtScheduleApiWebClient") private val webClient: WebClient, private val hmctsApiConfiguration: HmctsApiConfiguration, private val rateLimiter: HmctsRateLimiter) {
  fun getCourtSchedule(
    courtCaseRef: String,
  ): CourtScheduleResponse {
    rateLimiter.acquire()
    return webClient.get()
      .uri("/case/$courtCaseRef/courtschedule")
      .header(SUBSCRIPTION_KEY_HEADER, hmctsApiConfiguration.courtScheduleKey)
      .header(X_CORRELATION_ID_HEADER, WebClientConfiguration.getCorrelationId().toString())
      .retrieve()
      .bodyToMono<CourtScheduleResponse>()
      .block()!!
  }

  companion object {
    const val SUBSCRIPTION_KEY_HEADER = "Ocp-Apim-Subscription-Key"
  }
}
