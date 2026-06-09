package uk.gov.justice.digital.hmpps.courtdataingestionapi.client

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.reactive.function.client.toEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.courtdataingestionapi.config.WebClientConfiguration.Companion.X_CORRELATION_ID_HEADER
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.CourtScheduleResponse
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.CourthouseResponse
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsFile
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.SubscriptionCreatedResponse
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.SubscriptionRequest
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.SubscriptionUpdatedResponse
import uk.gov.justice.digital.hmpps.courtdataingestionapi.subscription.HmctsApiConfiguration
import java.util.UUID

@Component
class HmctsApiClient(@Qualifier("hmctsApiWebClient") private val webClient: WebClient, private val hmctsApiConfiguration: HmctsApiConfiguration) {

  fun createSubscription(request: SubscriptionRequest): SubscriptionCreatedResponse = webClient.post()
    .uri("/hrds/client-subscriptions")
    .header(SUBSCRIPTION_KEY_HEADER, hmctsApiConfiguration.subscriptionKey)
    .header(X_CORRELATION_ID_HEADER, WebClientConfiguration.getCorrelationId().toString())
    .bodyValue(request)
    .retrieve()
    .bodyToMono<SubscriptionCreatedResponse>()
    .block()!!

  fun updateSubscription(
    request: SubscriptionRequest,
    subscriptionId: String,
  ): SubscriptionUpdatedResponse = webClient.put()
    .uri("/hrds/client-subscriptions/$subscriptionId")
    .header(SUBSCRIPTION_KEY_HEADER, hmctsApiConfiguration.subscriptionKey)
    .header(X_CORRELATION_ID_HEADER, WebClientConfiguration.getCorrelationId().toString())
    .bodyValue(request)
    .retrieve()
    .bodyToMono<SubscriptionUpdatedResponse>()
    .block()!!

  fun getFile(clientSubscriptionId: String, externalFileId: UUID): HmctsFile = webClient.get()
    .uri("/hrds/client-subscriptions/$clientSubscriptionId/documents/$externalFileId")
    .header(SUBSCRIPTION_KEY_HEADER, hmctsApiConfiguration.subscriptionKey)
    .header(X_CORRELATION_ID_HEADER, WebClientConfiguration.getCorrelationId().toString())
    .retrieve()
    .toEntity<ByteArray>()
    .map { response ->
      val contentType = response.headers.contentType?.toString()
      val filename = extractFilename(response.headers)

      HmctsFile(
        bytes = response.body ?: ByteArray(0),
        name = "file",
        originalFilename = filename ?: "file.bin",
        contentType = contentType,
      )
    }.block()!!

  fun getCourtSchedule(
    courtCaseRef: String,
  ): CourtScheduleResponse = webClient.get()
    .uri("/slc/case/$courtCaseRef/courtschedule")
    .header(SUBSCRIPTION_KEY_HEADER, hmctsApiConfiguration.courtScheduleKey)
    .header(X_CORRELATION_ID_HEADER, WebClientConfiguration.getCorrelationId().toString())
    .retrieve()
    .bodyToMono<CourtScheduleResponse>()
    .block()!!

  fun getCourthouse(
    courthouseId: UUID,
  ): CourthouseResponse = webClient.get()
    .uri("/rcc/courthouses/$courthouseId")
    .header(SUBSCRIPTION_KEY_HEADER, hmctsApiConfiguration.courthouseKey)
    .header(X_CORRELATION_ID_HEADER, WebClientConfiguration.getCorrelationId().toString())
    .retrieve()
    .bodyToMono<CourthouseResponse>()
    .block()!!

  private fun extractFilename(headers: HttpHeaders): String? {
    val disposition = headers.contentDisposition
    return disposition.filename
  }

  companion object {
    const val SUBSCRIPTION_KEY_HEADER = "Ocp-Apim-Subscription-Key"
  }
}
