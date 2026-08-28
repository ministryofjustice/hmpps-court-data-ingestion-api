package uk.gov.justice.digital.hmpps.courtdataingestionapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "feature-toggles")
data class FeatureToggles(
  var defendantResolution: Boolean = false,
  var structuredExtraction: Boolean = false,
  val offenceDataEnabled: Boolean = false,
)
