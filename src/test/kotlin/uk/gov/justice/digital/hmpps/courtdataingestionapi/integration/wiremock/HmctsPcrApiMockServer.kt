package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.CourtRegisterApiMockServer.Companion.TEST_HMCTS_COURTHOUSE_ID_NO_REGISTER
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsSubcriptionApiMockServer.Companion.TEST_HMCTS_COURTHOUSE_ID
import java.util.UUID

class HmctsPcrApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val hmctsPcrApiMockServer = HmctsPcrApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    hmctsPcrApiMockServer.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    hmctsPcrApiMockServer.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    hmctsPcrApiMockServer.stop()
  }
}

class HmctsPcrApiMockServer : WireMockServer(WIREMOCK_PORT) {

  fun stubGetPcr(
    caseURN: String,
    hearingId: UUID,
    defendantId: UUID,
    response: String = DEFAULT_RESPONSE,
  ) {
    stubFor(
      get(urlEqualTo("/cases/$caseURN/hearings/$hearingId/defendants/$defendantId"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(response),
        ),
    )
  }

  fun stubGetPcrError(
    caseURN: String,
    hearingId: UUID,
    defendantId: UUID,
  ) {
    stubFor(
      get(urlEqualTo("/cases/$caseURN/hearings/$hearingId/defendants/$defendantId"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(500)
            .withBody("error"),
        ),
    )
  }

  companion object {
    private const val WIREMOCK_PORT = 8341
    private const val DEFAULT_RESPONSE = """
      [
  {
    "courtApplications": [],
    "defendant": {
      "address": {
        "address1": "99 Riverside Drive",
        "address2": "Riverside Drive",
        "address3": "Chester-le-Street",
        "postCode": "NW1 5BR"
      },
      "dateOfBirth": "1954-08-14",
      "firstName": "Cecil",
      "gender": "MALE",
      "id": "1c67dc29-d239-42b6-b458-0b0609dc609b",
      "lastName": "Brakus",
      "masterDefendantId": "a05a07fc-cb0a-450c-9500-02ca29efd2c1",
      "postHearingCustodyStatus": "Not Applicable",
      "results": [],
      "title": "Mr"
    },
    "hearing": {
      "courtDetails": {
        "court": {
          "courtHouseCode": "B01DU00",
          "courtHouseId": "$TEST_HMCTS_COURTHOUSE_ID",
          "courtHouseName": "City of London Magistrates' Court"
        },
        "courtAddress": {
          "address1": "1 Queen Victoria Street",
          "address2": "London",
          "address3": "",
          "address4": "",
          "address5": "",
          "postCode": "EC4N 4XY"
        },
        "ljaName": "Central London Magistrates' Court"
      },
      "hearingDate": "2026-08-15",
      "hearingType": "First hearing",
      "id": "27cc647d-0818-4a4b-87f3-c8ba15781d80",
      "jurisdiction": "MAGISTRATES",
      "nextHearing": {
        "court": {
          "courtHouseCode": "B01LY00",
          "courtHouseId": "$TEST_HMCTS_COURTHOUSE_ID_NO_REGISTER",
          "courtHouseName": "Lavender Hill Magistrates' Court"
        },
        "dateTime": "2026-08-15T09:00:00Z",
        "hearingId": "e2a6187c-26a0-4862-8041-beeb37e4b83a"
      }
    },
    "offences": [
      {
        "code": "TH68001",
        "listingNumber": 1,
        "offenceLegislation": "Contrary to section 1(1) and 7 of the Theft Act 1968.",
        "pleaDate": "2026-08-15",
        "pleaValue": "NOT_GUILTY",
        "results": [
          {
            "resultDescription": "RIB - Remanded in custody with bail direction",
            "resultTexts": [
              {
                "label": "Bail exception",
                "value": "Breach of bail"
              },
              {
                "label": "Next hearing in magistrates' court",
                "value": "Date of hearing:15/08/2026\nTime of hearing:10:00\nCourthouse organisation name:Lavender Hill Magistrates' Court\nCourthouse address line 1:176A Lavender Hill\nCourthouse address line 2:London\nCourthouse post code:SW11 1JU\nCourtroom:Courtroom 01\nHearing type:Appeal\nEstimated duration:30 MINUTES\nBooking reference:e2a6187c-26a0-4862-8041-beeb37e4b83a"
              },
              {
                "label": "Prison organisation name",
                "value": "HMP Ashfield"
              },
              {
                "label": "Prison email address 1",
                "value": "yoiashfield.premiercustody@premier-serco.cjsm.net"
              },
              {
                "label": "Prison email address 2",
                "value": "yoiashfield.premiercustody@premier-serco.cjsm.net"
              },
              {
                "label": "Conveyor / custodian name organisation name",
                "value": "Lavender Hill Magistrates' Court: PECS"
              },
              {
                "label": "Conveyor / custodian name email address 1",
                "value": "periodicwarrants@geoamey.co.uk"
              },
              {
                "label": "Remand basis",
                "value": "Charged with a violent or sexual offence"
              },
              {
                "label": "Bail exception reason",
                "value": "Broken bail conditions"
              },
              {
                "label": "To attend or a warrant to issue",
                "value": ""
              },
              {
                "label": "Adjournment reasons",
                "value": ""
              }
            ]
          }
        ],
        "startDate": "2026-06-15",
        "endDate": "2026-07-15",
        "title": "Theft from the person of another",
        "wording": "Theft from the person of another"
      }
    ],
    "prosecutionCase": {
      "caseMarkers": [],
      "caseURN": "ZZ231257861",
      "results": []
    }
  }
]
    """
  }
}
