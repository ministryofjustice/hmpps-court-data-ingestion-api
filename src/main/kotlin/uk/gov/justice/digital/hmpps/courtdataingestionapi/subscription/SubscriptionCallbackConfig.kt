package uk.gov.justice.digital.hmpps.courtdataingestionapi.subscription

import org.springframework.boot.context.properties.ConfigurationProperties
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType

@ConfigurationProperties(prefix = "subscription-callback-config")
data class SubscriptionCallbackConfig(
  val callbackUrl: String,
  val subscriptionKey: String,
  val eventTypes: List<HmctsEventType>,
)
