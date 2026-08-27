package uk.gov.justice.digital.hmpps.courtdataingestionapi.client

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ArrayNode
import uk.gov.justice.digital.hmpps.courtdataingestionapi.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.courtdataingestionapi.config.WebClientConfiguration.Companion.X_CORRELATION_ID_HEADER
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsPcr
import uk.gov.justice.digital.hmpps.courtdataingestionapi.subscription.HmctsApiConfiguration
import java.util.UUID

@Component
class HmctsPcrApiClient(
  @Qualifier("hmctsPcrApiWebClient") private val webClient: WebClient,
  private val hmctsApiConfiguration: HmctsApiConfiguration,
  private val rateLimiter: HmctsRateLimiter,
  private val objectMapper: ObjectMapper,
) {
  fun get(
    caseURN: String,
    hearingId: UUID,
    defendantId: UUID,
  ): ApiArrayResponseWithRawJson<HmctsPcr> {
    rateLimiter.acquire()
    return webClient.get()
      .uri("/cases/$caseURN/hearings/$hearingId/defendants/$defendantId")
      .header(HmctsApiConfiguration.SUBSCRIPTION_KEY_HEADER, hmctsApiConfiguration.pcrKey)
      .header(X_CORRELATION_ID_HEADER, WebClientConfiguration.getCorrelationId().toString())
      .retrieve()
      .bodyToMono<ArrayNode>()
      .map { json ->
        val pcrs: List<HmctsPcr> = objectMapper.convertValue(
          json,
          object : TypeReference<List<HmctsPcr>>() {},
        )
        ApiArrayResponseWithRawJson(
          raw = json,
          data = pcrs,
        )
      }
      .block()!!
  }
}

data class ApiArrayResponseWithRawJson<T>(
  val raw: ArrayNode,
  val data: List<T>,
)
