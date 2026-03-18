package uk.gov.justice.digital.hmpps.courtdataingestionapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "hmpps.secret")
data class HmppsSecretManagerProperties(
  val provider: String = "aws",
  val region: String = "eu-west-2",
  val localstackUrl: String = "http://localhost:4566",
  val secretId: String = "hmpps-secret-id",
)
