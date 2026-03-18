package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi

data class SubscriptionResponse(
  val clientSubscriptionId: String,
  val hmac: HmacCredentials,
)
