package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class HmctsCourthouseApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val hmctsCourthouseApi = HmctsCourthouseApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    hmctsCourthouseApi.stubCourthouse()
    hmctsCourthouseApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    hmctsCourthouseApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    hmctsCourthouseApi.stop()
  }
}

class HmctsCourthouseApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8338
    const val TEST_HMCTS_COURTHOUSE_ID = "e2d1bad5-0222-485a-a6ca-6d01a8804db6"
  }

  fun stubCourthouse() {
    stubFor(
      get(urlEqualTo("/courthouses/$TEST_HMCTS_COURTHOUSE_ID"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(
              """
              {
                "courtHouseType": "crown",
                "courtHouseCode": "LND001",
                "courtHouseName": "Central London County Court",
                "address": {
                  "address1": "Thomas More Building",
                  "address2": "Royal Courts of Justice",
                  "address3": "Strand",
                  "address4": "London",
                  "postalCode": "WC2A 2LL",
                  "country": "UK"
                }
              }
              """.trimIndent(),
            ),
        ),
    )
  }
}
