package uk.gov.justice.digital.hmpps.courtdataingestionapi.subscription

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "subscription-callback-config")
data class SubscriptionCallbackConfig(
  val callbackUrl: String,
  val subscriptionKey: String,
)
