package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsSubcriptionApiMockServer.Companion.TEST_HMCTS_COURTHOUSE_ID

class CourtRegisterApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val courtRegisterApi = CourtRegisterApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    courtRegisterApi.start()
    courtRegisterApi.stubCourt()
  }

  override fun beforeEach(context: ExtensionContext) {
    courtRegisterApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    courtRegisterApi.stop()
  }
}

class CourtRegisterApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8340
    const val TEST_HMCTS_COURTHOUSE_ID_NO_REGISTER = "f2d1bad6-0333-485b-a6ca-7d01a8804dc7"
    const val COURT_REGISTER_BODY: String = """
              {
                "courtId": "LND001",
                "courtName": "Central London County Court",
                "courtDescription": "Central London County Court"
              }
              """
  }

  fun stubCourt() {
    stubFor(
      get(urlEqualTo("/courts/cp/$TEST_HMCTS_COURTHOUSE_ID"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(COURT_REGISTER_BODY.trimIndent(),
            ),
        ),
    )
//    stubFor(
//      get(urlEqualTo("/courts/cp/$TEST_HMCTS_COURTHOUSE_ID_NO_REGISTER"))
//        .willReturn(
//          aResponse().withStatus(404),
//        ),
//    )
  }

  fun stubHmctsCourt(hmctsCourtId: String) {
    stubFor(
      get(urlEqualTo("/courts/cp/$hmctsCourtId"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(COURT_REGISTER_BODY.trimIndent(),
            ),
        ),
    )
  }
}
