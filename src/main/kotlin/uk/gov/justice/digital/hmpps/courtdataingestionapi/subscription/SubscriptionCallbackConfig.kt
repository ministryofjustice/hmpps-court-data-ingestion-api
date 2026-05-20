package uk.gov.justice.digital.hmpps.courtdataingestionapi.subscription

import org.springframework.boot.context.properties.ConfigurationProperties
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType

@ConfigurationProperties(prefix = "subscription-callback-config")
data class SubscriptionCallbackConfig(
  val callbackUrl: String,
  val subscriptionKey: String,
  /* A NULL list of event types will subscribe to all event types. */
  val eventTypes: List<HmctsEventType>?,
)
