package uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("document.content-normalisation")
data class ContentNormalisationProperties(
  val patterns: List<String> = emptyList(),
)
