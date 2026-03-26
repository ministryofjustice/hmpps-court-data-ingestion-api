package uk.gov.justice.digital.hmpps.courtdataingestionapi.client

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsFile
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.SubscriptionRequest
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.SubscriptionResponse

@Component
class HmctsSubscriptionApiClient(@Qualifier("hmctsSubscriptionApiWebClient") private val webClient: WebClient) {

  fun createSubscription(request: SubscriptionRequest, subscriptionKey: String): SubscriptionResponse = webClient.post()
    .uri("/client-subscriptions")
    .header(SUBSCRIPTION_KEY_HEADER, subscriptionKey)
    .bodyValue(request)
    .retrieve()
    .bodyToMono(SubscriptionResponse::class.java)
    .block()!!

  fun updateSubscription(
    request: SubscriptionRequest,
    subscriptionKey: String,
    subscriptionId: String,
  ): SubscriptionResponse = webClient.put()
    .uri("/client-subscriptions/$subscriptionId")
    .header(SUBSCRIPTION_KEY_HEADER, subscriptionKey)
    .bodyValue(request)
    .retrieve()
    .bodyToMono(SubscriptionResponse::class.java)
    .block()!!

  fun getFile(clientSubscriptionId: String, externalFileId: String): HmctsFile = webClient.get()
    .uri("/client-subscriptions/$clientSubscriptionId/documents/$externalFileId")
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
