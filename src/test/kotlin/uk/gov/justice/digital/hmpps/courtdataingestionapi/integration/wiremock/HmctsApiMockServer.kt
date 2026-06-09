package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.core.io.ClassPathResource
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase

class HmctsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val hmctsApi = HmctsApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    hmctsApi.stubCreateSubscription()
    hmctsApi.stubUpdateSubscription()
    hmctsApi.stubFile()
    hmctsApi.stubCourtSchedule()
    hmctsApi.stubCourthouse()
    hmctsApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    hmctsApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    hmctsApi.stop()
  }
}

class HmctsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8333
    const val TEST_SUBSCRIPTION_ID = "a5c06879-ee4e-4ebd-90ec-8a85efc1aed2"
    const val TEST_HMAC_KEY = "ef026f37-7552-4fb7-8e22-72243188b4a3"
    const val TEST_HMCTS_HEARING_ID = "e4ee99d2-8cfd-4444-8ef1-d79b93e0cdec"
    const val TEST_HMCTS_COURTHOUSE_ID = "e2d1bad5-0222-485a-a6ca-6d01a8804db6"
  }

  fun stubCreateSubscription() {
    stubFor(
      post(urlEqualTo("/hrds/client-subscriptions"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(
              """
              {
                "clientSubscriptionId": "$TEST_SUBSCRIPTION_ID",
                "hmac": {
                  "secret": "$TEST_HMAC_KEY"
                }
              }
              """.trimIndent(),
            ),
        ),
    )
  }

  fun stubUpdateSubscription() {
    stubFor(
      put(urlEqualTo("/hrds/client-subscriptions/$TEST_SUBSCRIPTION_ID"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(
              """
              {
                "clientSubscriptionId": "$TEST_SUBSCRIPTION_ID"
              }
              """.trimIndent(),
            ),
        ),
    )
  }

  fun stubFile() {
    stubFor(
      get(urlEqualTo("/hrds/client-subscriptions/$TEST_SUBSCRIPTION_ID/documents/${IntegrationTestBase.COURT_DOCUMENT_ID}"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "text/plain")
            .withStatus(200)
            .withBody(ClassPathResource("test.txt").contentAsByteArray)
            .withHeader("Content-Disposition", "attachment; filename=\"test.txt\""),
        ),
    )
  }

  fun stubCourtSchedule() {
    stubFor(
      get(urlEqualTo("/slc/case/${IntegrationTestBase.CASE_REFERENCE}/courtschedule"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(
              """
              {
                  "courtSchedule": [
                      {
                          "hearings": [
                              {
                                  "courtSittings": [
                                      {
                                          "courtHouse": "$TEST_HMCTS_COURTHOUSE_ID",
                                          "courtRoom": "b4562684-9209-3ec4-a544-7f80dabd94d8",
                                          "judiciaryId": "",
                                          "sittingEnd": "2026-06-04T11:20:00Z",
                                          "sittingStart": "2026-06-04T11:00:00Z"
                                      }
                                  ],
                                  "hearingDescription": "First hearing",
                                  "hearingId": "$TEST_HMCTS_HEARING_ID",
                                  "hearingType": "First hearing",
                                  "listNote": ""
                              }
                          ]
                      }
                  ]
              }
              """.trimIndent(),
            ),
        ),
    )
  }

  fun stubCourthouse() {
    stubFor(
      get(urlEqualTo("/rcc/courthouses/$TEST_HMCTS_COURTHOUSE_ID"))
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
