package uk.gov.justice.digital.hmpps.courtdataingestionapi.client

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.subscription.HmctsApiConfiguration
import java.time.LocalDate
import java.util.UUID

class HmctsCourtDefendantApiClientTest {

  private lateinit var server: WireMockServer
  private lateinit var client: HmctsCourtDefendantApiClient
  private val config = mock<HmctsApiConfiguration>()

  private val caseUrn = "20GD1234567"
  private val master = UUID.fromString("a1f2e3d4-c5b6-4789-9abc-1d2e3f4a5b6c")
  private val defendant = UUID.fromString("30b83084-d2fe-4a70-bbd1-fae1dfdb4b95")

  @BeforeEach
  fun setUp() {
    whenever(config.courtDefendantKey).thenReturn("test-apim-key")
    server = WireMockServer(options().dynamicPort())
    server.start()
    val webClient = WebClient.builder().baseUrl("http://localhost:${server.port()}").build()
    client = HmctsCourtDefendantApiClient(webClient, config)
  }

  @AfterEach
  fun tearDown() = server.stop()

  @Test
  fun `parses defendants, passes masterDefendantId, and sends the APIM subscription key`() {
    server.stubFor(
      get(urlPathEqualTo("/defendants/cases/$caseUrn"))
        .withQueryParam("masterDefendantId", equalTo(master.toString()))
        .willReturn(
          aResponse().withStatus(200).withHeader("Content-Type", "application/json")
            .withBody(
              """[{"defendantId":"$defendant","masterDefendantId":"$master","name":"John Doe","dateOfBirth":"1980-01-31"}]""",
            ),
        ),
    )

    val result = client.getDefendants(caseUrn, masterDefendantId = master)

    assertThat(result).hasSize(1)
    assertThat(result[0].defendantId).isEqualTo(defendant)
    assertThat(result[0].masterDefendantId).isEqualTo(master)
    assertThat(result[0].dateOfBirth).isEqualTo(LocalDate.of(1980, 1, 31))
    server.verify(
      getRequestedFor(urlPathEqualTo("/defendants/cases/$caseUrn"))
        .withHeader("Ocp-Apim-Subscription-Key", equalTo("test-apim-key")),
    )
  }

  @Test
  fun `parses a defendant with an empty name and no date of birth`() {
    server.stubFor(
      get(urlPathEqualTo("/defendants/cases/$caseUrn"))
        .willReturn(
          aResponse().withStatus(200).withHeader("Content-Type", "application/json")
            .withBody("""[{"defendantId":"$defendant","masterDefendantId":"$master","name":""}]"""),
        ),
    )

    val result = client.getDefendants(caseUrn)

    assertThat(result).hasSize(1)
    assertThat(result[0].name).isEmpty()
    assertThat(result[0].dateOfBirth).isNull()
  }

  @Test
  fun `returns an empty list when the case has no matching defendants`() {
    server.stubFor(
      get(urlPathEqualTo("/defendants/cases/$caseUrn"))
        .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("[]")),
    )

    assertThat(client.getDefendants(caseUrn)).isEmpty()
  }

  @Test
  fun `treats an unknown case URN (404) as an empty list, not a failure`() {
    server.stubFor(
      get(urlPathEqualTo("/defendants/cases/$caseUrn")).willReturn(aResponse().withStatus(404)),
    )

    assertThat(client.getDefendants(caseUrn)).isEmpty()
  }
}
