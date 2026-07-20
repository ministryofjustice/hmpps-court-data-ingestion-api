package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.CourtRegisterApiMockServer.Companion.TEST_HMCTS_COURTHOUSE_ID_NO_REGISTER
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsSubcriptionApiMockServer.Companion.TEST_HMCTS_COURTHOUSE_ID
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsSubcriptionApiMockServer.Companion.TEST_HMCTS_HEARING_ID

class HmctsCourtScheduleApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val hmctsCourtScheduleApi = HmctsCourtScheduleApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    hmctsCourtScheduleApi.stubCourtSchedule()
    hmctsCourtScheduleApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    hmctsCourtScheduleApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    hmctsCourtScheduleApi.stop()
  }
}

class HmctsCourtScheduleApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8337
  }

  fun stubCourtSchedule() {
    stubFor(
      get(urlEqualTo("/case/${IntegrationTestBase.CASE_REFERENCE}/courtschedule"))
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

  fun stubCourtScheduleWithoutCourtRegistry() {
    stubFor(
      get(urlEqualTo("/case/${IntegrationTestBase.CASE_REFERENCE}/courtschedule"))
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
                                          "courtHouse": "$TEST_HMCTS_COURTHOUSE_ID_NO_REGISTER",
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
}
