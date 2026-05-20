package uk.gov.justice.digital.hmpps.courtdataingestionapi.client

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsFile
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.SubscriptionCreatedResponse
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.SubscriptionRequest
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.SubscriptionUpdatedResponse
import java.util.UUID

@Component
class HmctsSubscriptionApiClient(@Qualifier("hmctsSubscriptionApiWebClient") private val webClient: WebClient) {

  fun createSubscription(request: SubscriptionRequest, subscriptionKey: String): SubscriptionCreatedResponse = webClient.post()
    .uri("/client-subscriptions")
    .header(SUBSCRIPTION_KEY_HEADER, subscriptionKey)
    .bodyValue(request)
    .retrieve()
    .bodyToMono(SubscriptionCreatedResponse::class.java)
    .block()!!

  fun updateSubscription(
    request: SubscriptionRequest,
    subscriptionKey: String,
    subscriptionId: String,
  ): SubscriptionUpdatedResponse = webClient.put()
    .uri("/client-subscriptions/$subscriptionId")
    .header(SUBSCRIPTION_KEY_HEADER, subscriptionKey)
    .bodyValue(request)
    .retrieve()
    .bodyToMono(SubscriptionUpdatedResponse::class.java)
    .block()!!

  fun getFile(clientSubscriptionId: String, externalFileId: UUID, subscriptionKey: String): HmctsFile = webClient.get()
    .uri("/client-subscriptions/$clientSubscriptionId/documents/$externalFileId")
    .header(SUBSCRIPTION_KEY_HEADER, subscriptionKey)
    .retrieve()
    .toEntity(ByteArray::class.java)
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

  private fun extractFilename(headers: HttpHeaders): String? {
    val disposition = headers.contentDisposition
    return disposition.filename
  }

  companion object {
    const val SUBSCRIPTION_KEY_HEADER = "Ocp-Apim-Subscription-Key"
  }
}
