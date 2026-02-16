package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi

data class SubscriptionRequest(
  val notificationEndpoint: NotificationEndpoint,
  val eventTypes: List<String>,
)
