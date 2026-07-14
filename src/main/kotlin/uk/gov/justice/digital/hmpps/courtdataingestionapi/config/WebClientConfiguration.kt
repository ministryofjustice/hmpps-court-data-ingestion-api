package uk.gov.justice.digital.hmpps.courtdataingestionapi.config

import io.opentelemetry.api.trace.Span
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.authorisedWebClient
import uk.gov.justice.hmpps.kotlin.auth.healthWebClient
import java.time.Duration
import java.util.UUID
import java.util.regex.Pattern

@Configuration
class WebClientConfiguration(
  @param:Value("\${hmpps-auth.url}") val hmppsAuthBaseUri: String,
  @param:Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @param:Value("\${api.timeout:20s}") val timeout: Duration,
  @param:Value("\${core.person.api.url}") private val corePersonApiUrl: String,
  @param:Value("\${hmpps.document.management.api.url}") private val hmppsDocumentManagementApiUrl: String,
  @param:Value("\${hmpps.court-cases-release-dates.api.url}") private val courtCasesReleaseDatesApiUrl: String,
  @param:Value("\${hmcts.subscription.api.url}") private val hmctsSubscriptionApiUrl: String,
  @param:Value("\${hmcts.courthouse.api.url}") private val hmctsCourthouseApiUrl: String,
  @param:Value("\${hmcts.court.schedule.api.url}") private val hmctsCourtScheduleApiUrl: String,
  @param:Value("\${prisoner.search.api.url}") private val prisonerSearchApiUrl: String,
  @param:Value("\${hmcts.court.defendant.api.url}") private val hmctsCourtDefendantApiUrl: String,
) {
  // HMPPS Auth health ping is required if your service calls HMPPS Auth to get a token to call other services
  @Bean
  fun hmppsAuthHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(hmppsAuthBaseUri, healthTimeout)

  @Bean
  fun corePersonApiWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.authorisedWebClient(
    authorizedClientManager,
    "core-person-api",
    corePersonApiUrl,
  )

  @Bean
  fun hmppsDocumentManagementApiWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.authorisedWebClient(
    authorizedClientManager,
    "hmpps-document-management-api",
    hmppsDocumentManagementApiUrl,
  )

  @Bean
  fun hmppsCourtCasesReleaseDatesApiWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.authorisedWebClient(
    authorizedClientManager,
    "hmpps-court-cases-release-dates-api",
    courtCasesReleaseDatesApiUrl,
  )

  @Bean
  fun hmctsSubscriptionApiWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.authorisedWebClient(
    authorizedClientManager,
    "hmcts-subscription-api",
    hmctsSubscriptionApiUrl,
  )

  @Bean
  fun hmctsCourthouseApiWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.authorisedWebClient(
    authorizedClientManager,
    "hmcts-courthouse-api",
    hmctsCourthouseApiUrl,
  )

  @Bean
  fun hmctsCourtScheduleApiWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.authorisedWebClient(
    authorizedClientManager,
    "hmcts-court-schedule-api",
    hmctsCourtScheduleApiUrl,
  )

  @Bean
  fun prisonerSearchApiWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.authorisedWebClient(
    authorizedClientManager,
    "prisoner-search-api",
    prisonerSearchApiUrl,
  )

  @Bean
  fun hmctsCourtDefendantApiWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.authorisedWebClient(
    authorizedClientManager,
    "hmcts-court-defendant-api",
    hmctsCourtDefendantApiUrl,
  )

  companion object {
    const val X_CORRELATION_ID_HEADER = "X-Correlation-Id"
    const val TRACE_ID_EMPTY = "00000000000000000000000000000000"
    const val X_CORRELATION_ID_REGEX = "([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]+)"
    const val X_CORRELATION_ID_RESULT_TEMPLATE = "$1-$2-$3-$4-$5"

    val log: Logger = LoggerFactory.getLogger(this::class.java)

    fun getCorrelationId(): UUID {
      try {
        val traceId = Span.current().spanContext.traceId
        return UUID.fromString(convertCorrelationId(traceId))
      } catch (err: IllegalArgumentException) {
        UUID.randomUUID().let {
          log.info("Using {}=[{}]. Caused by Trace Id wrong format :: {}", X_CORRELATION_ID_HEADER, it, err.message)
          return it
        }
      }
    }

    fun convertCorrelationId(traceId: String): String {
      if (traceId == TRACE_ID_EMPTY) return traceId

      return Pattern.compile(X_CORRELATION_ID_REGEX).matcher(traceId).replaceAll(X_CORRELATION_ID_RESULT_TEMPLATE)
    }
  }
}
