package uk.gov.justice.digital.hmpps.courtdataingestionapi.subscription

import org.springframework.boot.context.properties.ConfigurationProperties
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType

@ConfigurationProperties(prefix = "hmcts-api-config")
data class HmctsApiConfiguration(
  val enabled: Boolean,
  val callbackUrl: String,
  val subscriptionKey: String,
  private val eventTypes: List<String>,
  val updateSubscriptionOnStartup: Boolean,
  val courthouseKey: String,
  val courtScheduleKey: String,
  val courtDefendantKey: String,
  /*
   * Rate limit for requests to HMCTS API.
   * HMCTS have a 100request per min rate limit
   * There are 4 pods in preprod/prod and 2 in dev
   * In prod 25 requests per min per pod. 0.4 per second
   * In dev 50 requests per min per pod. 0.8 per second
   */
  val rateLimit: Double,
) {

  fun getEventTypesToSubscribe(): List<HmctsEventType> = if (eventTypes.contains(SUBSCRIBE_TO_ALL_EVENT_TYPES)) {
    HmctsEventType.entries
  } else {
    eventTypes.map { HmctsEventType.valueOf(it) }
  }

  companion object {
    const val SUBSCRIBE_TO_ALL_EVENT_TYPES = "ALL_EVENT_TYPES"
  }
}
