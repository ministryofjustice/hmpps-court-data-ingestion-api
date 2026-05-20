package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi

data class SubscriptionCreatedResponse(
  val clientSubscriptionId: String,
  val hmac: HmacCredentials,
)
