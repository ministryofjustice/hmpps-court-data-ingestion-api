package uk.gov.justice.digital.hmpps.courtdataingestionapi.client

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.SubscriptionRequest
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.SubscriptionResponse

@Component
class HmctsSubscriptionApiClient(@Qualifier("hmctsSubscriptionApiWebClient") private val webClient: WebClient) {

  fun subscribe(request: SubscriptionRequest, subscriptionKey: String): SubscriptionResponse = webClient.post()
    .uri("/client-subscriptions")
    .header(SUBSCRIPTION_KEY_HEADER, subscriptionKey)
    .bodyValue(request)
    .retrieve()
    .bodyToMono(SubscriptionResponse::class.java)
    .block()!!

  companion object {
    const val SUBSCRIPTION_KEY_HEADER = "X-HMCTS-Subscription-Key"
  }
}
