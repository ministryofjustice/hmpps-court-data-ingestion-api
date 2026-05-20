package uk.gov.justice.digital.hmpps.courtdataingestionapi.subscription

import org.springframework.boot.context.properties.ConfigurationProperties
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType

@ConfigurationProperties(prefix = "subscription-callback-config")
data class SubscriptionCallbackConfig(
  val callbackUrl: String,
  val subscriptionKey: String,
  private val eventTypes: List<String>,
  val updateSubscriptionOnStartup: Boolean
) {


  fun getEventTypesToSubscribe(): List<HmctsEventType> {
    return if (eventTypes.contains(SUBSCRIBE_TO_ALL_EVENT_TYPES)) {
      HmctsEventType.entries
    } else {
      eventTypes.map { HmctsEventType.valueOf(it) }
    }
  }

  companion object {
    const val SUBSCRIBE_TO_ALL_EVENT_TYPES = "ALL_EVENT_TYPES"
  }
}
