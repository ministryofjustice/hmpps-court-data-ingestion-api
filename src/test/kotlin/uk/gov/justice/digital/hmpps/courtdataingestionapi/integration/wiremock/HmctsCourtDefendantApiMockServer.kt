package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.TestUtil
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.DefendantDetails

class HmctsCourtDefendantApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val hmctsCourtDefendantApi = HmctsCourtDefendantApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    hmctsCourtDefendantApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    hmctsCourtDefendantApi.resetAll()
    hmctsCourtDefendantApi.stubNoDefendants()
  }

  override fun afterAll(context: ExtensionContext) {
    hmctsCourtDefendantApi.stop()
  }
}

class HmctsCourtDefendantApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8339
  }

  /** Default: no defendants for any case, so live resolution falls back to the master id. */
  fun stubNoDefendants() {
    stubFor(
      get(urlPathMatching("/defendants/cases/.*"))
        .atPriority(10)
        .willReturn(jsonResponse("[]")),
    )
  }

  fun stubDefendants(caseReference: String, defendants: List<DefendantDetails>) {
    stubFor(
      get(urlPathEqualTo("/defendants/cases/$caseReference"))
        .willReturn(jsonResponse(TestUtil.objectMapper().writeValueAsString(defendants))),
    )
  }

  private fun jsonResponse(body: String) = aResponse()
    .withHeader("Content-Type", "application/json")
    .withStatus(200)
    .withBody(body)
}
