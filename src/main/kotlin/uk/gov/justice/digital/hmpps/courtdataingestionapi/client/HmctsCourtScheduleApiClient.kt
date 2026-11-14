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
class HmctsCourtScheduleApiClient(
  @Qualifier("hmctsCourtScheduleApiWebClient") private val webClient: WebClient,
  private val hmctsApiConfiguration: HmctsApiConfiguration,
  private val rateLimiter: HmctsRateLimiter,
) {
  fun getCourtSchedule(courtCaseRef: String): CourtScheduleResponse = request(courtCaseRef)
    .retrieve()
    .bodyToMono<CourtScheduleResponse>()
    .block()!!

  fun getCourtScheduleRaw(courtCaseRef: String): RawCourtSchedule = request(courtCaseRef)
    .exchangeToMono { response ->
      response.bodyToMono<String>()
        .defaultIfEmpty("")
        .map { RawCourtSchedule(response.statusCode().value(), it) }
    }
    .block()!!

  private fun request(courtCaseRef: String): WebClient.RequestHeadersSpec<*> {
    rateLimiter.acquire()
    return webClient.get()
      .uri("/case/{caseRef}/courtschedule", courtCaseRef)
      .header(HmctsApiConfiguration.SUBSCRIPTION_KEY_HEADER, hmctsApiConfiguration.courtScheduleKey)
      .header(X_CORRELATION_ID_HEADER, WebClientConfiguration.getCorrelationId().toString())
  }
}

data class RawCourtSchedule(
  val status: Int,
  val body: String,
)
