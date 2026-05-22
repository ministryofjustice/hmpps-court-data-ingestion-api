package uk.gov.justice.digital.hmpps.courtdataingestionapi.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.format.FormatModelRegistry

@Configuration
class FormatModelConfiguration {
  @Bean
  fun formatModelRegistry(objectMapper: ObjectMapper): FormatModelRegistry = FormatModelRegistry.fromResources(
    mapper = objectMapper,
    activeKey = "prison-court-register:v1",
    resourcePaths = listOf("formats/prison-court-register-v1.json"),
  )
}
