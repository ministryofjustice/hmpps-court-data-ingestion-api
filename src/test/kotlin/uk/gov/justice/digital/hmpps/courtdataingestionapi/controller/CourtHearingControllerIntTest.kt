package uk.gov.justice.digital.hmpps.courtdataingestionapi.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.CorePersonApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsCourtDefendantApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsPcrApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsSubcriptionApiMockServer
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsSubcriptionApiMockServer.Companion.TEST_HMCTS_COURTHOUSE_ID
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsSubcriptionApiMockServer.Companion.TEST_HMCTS_HEARING_ID
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtHearing
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.DefendantDetails
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsCourt
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsCourtDetails
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsHearing
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsNextHearing
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsOffence
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsPcr
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsResult
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsResultText
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtHearingRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.typeReference
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

@TestPropertySource(
  properties = [
    "feature-toggles.offence-data-enabled=true",
  ],
)
class CourtHearingControllerIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Autowired
  private lateinit var courtHearingRepository: CourtHearingRepository

  @Nested
  @DisplayName("Get court hearing test")
  inner class GetCourtHearingTests {
    // TODO what data changes per each defendant?

    @Test
    fun `Get court hearing for matching hearing`() {
      val defendantId = UUID.randomUUID()
      HmctsCourtDefendantApiExtension.hmctsCourtDefendantApi.stubDefendants(
        CASE_REFERENCE,
        listOf(
          DefendantDetails(defendantId, MATCHING_CORE_PERSON),
        ),
      )
      HmctsPcrApiExtension.hmctsPcrApiMockServer.stubGetPcr(
        CASE_REFERENCE,
        UUID.fromString(HmctsSubcriptionApiMockServer.TEST_HMCTS_HEARING_ID),
        defendantId,
      )
      CorePersonApiExtension.corePersonApi.stubCommonPlatformCorePerson(defendantId, listOf(MATCHING_PRISONER_NUMBER))
      sendSubscriptionNotification(MATCHING_CORE_PERSON)

      val hearing = getCourtHearing(MATCHING_PRISONER_NUMBER, HmctsSubcriptionApiMockServer.TEST_HMCTS_HEARING_ID)

      assertThat(hearing.hearingId).isEqualTo(UUID.fromString(HmctsSubcriptionApiMockServer.TEST_HMCTS_HEARING_ID))
      assertThat(hearing.courtName).isEqualTo("Central London County Court")
      assertThat(hearing.courtId).isEqualTo(UUID.fromString("e2d1bad5-0222-485a-a6ca-6d01a8804db6"))
      assertThat(hearing.courtCode).isEqualTo("LND001")
      assertThat(hearing.hearingDate).isEqualTo(LocalDate.of(2026, 8, 15))
      assertThat(hearing.caseReferences).isEqualTo(listOf("CASE123456"))
      assertThat(hearing.hearingType).isEqualTo("First hearing")
      assertThat(hearing.documents.size).isEqualTo(1)
    }

    @Test
    @Transactional(readOnly = true)
    fun `Ingestion of updated court hearing`() {
      val defendantId = UUID.randomUUID()
      HmctsCourtDefendantApiExtension.hmctsCourtDefendantApi.stubDefendants(
        CASE_REFERENCE,
        listOf(
          DefendantDetails(defendantId, MATCHING_CORE_PERSON),
        ),
      )
      CorePersonApiExtension.corePersonApi.stubCommonPlatformCorePerson(defendantId, listOf(MATCHING_PRISONER_NUMBER))
      HmctsPcrApiExtension.hmctsPcrApiMockServer.stubGetPcr(
        CASE_REFERENCE,
        UUID.fromString(HmctsSubcriptionApiMockServer.TEST_HMCTS_HEARING_ID),
        defendantId,
      )
      sendSubscriptionNotification(MATCHING_CORE_PERSON)

      var hearing = getCourtHearing(MATCHING_PRISONER_NUMBER, HmctsSubcriptionApiMockServer.TEST_HMCTS_HEARING_ID)
      assertThat(hearing.hearingType).isEqualTo("First hearing")

      HmctsPcrApiExtension.hmctsPcrApiMockServer.stubGetPcr(
        CASE_REFERENCE,
        UUID.fromString(TEST_HMCTS_HEARING_ID),
        defendantId,
        objectMapper.writeValueAsString(listOf(UPDATED_HEARING)),
      )
      sendSubscriptionNotification(MATCHING_CORE_PERSON)
      hearing = getCourtHearing(MATCHING_PRISONER_NUMBER, HmctsSubcriptionApiMockServer.TEST_HMCTS_HEARING_ID)
      assertThat(hearing.hearingType).isEqualTo("Second hearing")

      val dbHearing = courtHearingRepository.findFirstByHmctsCourtHearingId(UUID.fromString(TEST_HMCTS_HEARING_ID))
      assertThat(dbHearing!!.courtCharges.size).isEqualTo(1)
      assertThat(dbHearing.nextCourtHearings.size).isEqualTo(1)
    }

    @Test
    fun `Document still ingested if error in getting hearing data`() {
      val defendantId = UUID.randomUUID()
      HmctsCourtDefendantApiExtension.hmctsCourtDefendantApi.stubDefendants(
        CASE_REFERENCE,
        listOf(
          DefendantDetails(defendantId, MATCHING_CORE_PERSON),
        ),
      )
      HmctsPcrApiExtension.hmctsPcrApiMockServer.stubGetPcrError(
        CASE_REFERENCE,
        UUID.fromString(TEST_HMCTS_HEARING_ID),
        defendantId,
      )
      CorePersonApiExtension.corePersonApi.stubCommonPlatformCorePerson(defendantId, listOf(MATCHING_PRISONER_NUMBER))
      sendSubscriptionNotification(MATCHING_CORE_PERSON)

      webTestClient
        .get()
        .uri("/court-hearings/prisoner/$MATCHING_PRISONER_NUMBER/hearing/$TEST_HMCTS_HEARING_ID")
        .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RO")))
        .exchange()
        .expectStatus()
        .isNotFound
    }

    @Test
    fun `Get court hearing for not found hearing`() {
      webTestClient
        .get()
        .uri("/court-hearings/prisoner/$MATCHING_PRISONER_NUMBER/hearing/${UUID.randomUUID()}")
        .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RO")))
        .exchange()
        .expectStatus()
        .isNotFound
    }

    @Test
    fun `Get court hearing where prisoner number does not match hearing documents`() {
      webTestClient
        .get()
        .uri("/court-hearings/prisoner/ANOTHERPRISONER/hearing/${HmctsSubcriptionApiMockServer.TEST_HMCTS_HEARING_ID}")
        .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RO")))
        .exchange()
        .expectStatus()
        .isNotFound
    }

    private fun getCourtHearing(prisonerNumber: String, hearingId: String): CourtHearing = webTestClient
      .get()
      .uri("/court-hearings/prisoner/$prisonerNumber/hearing/$hearingId")
      .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RO")))
      .exchange()
      .expectStatus()
      .isOk
      .expectBody<CourtHearing>()
      .returnResult().responseBody!!
  }

  @Nested
  @DisplayName("Get court hearing by prisoner tests")
  inner class GetCourtHearingByPrisonerTests {

    @Test
    fun `Get court hearing by prisoner`() {
      sendSubscriptionNotification(MATCHING_CORE_PERSON)
      val hearings = webTestClient
        .get()
        .uri("/court-hearings/prisoner/$MATCHING_PRISONER_NUMBER")
        .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RO")))
        .exchange()
        .expectBody(typeReference<List<CourtHearing>>())
        .returnResult().responseBody!!

      assertThat(hearings.size).isEqualTo(1)
      val hearing = hearings.first()
      assertThat(hearing.hearingId).isEqualTo(UUID.fromString(HmctsSubcriptionApiMockServer.TEST_HMCTS_HEARING_ID))
      assertThat(hearing.courtName).isEqualTo("Central London County Court")
      assertThat(hearing.courtId).isEqualTo(UUID.fromString("e2d1bad5-0222-485a-a6ca-6d01a8804db6"))
      assertThat(hearing.courtCode).isEqualTo("LND001")
      assertThat(hearing.hearingDate).isEqualTo(LocalDate.of(2026, 6, 4))
      assertThat(hearing.caseReferences).isEqualTo(listOf("CASE123456"))
      assertThat(hearing.hearingType).isEqualTo("First hearing")
      assertThat(hearing.documents.size).isEqualTo(1)
    }

    @Test
    fun `Get court hearings none found`() {
      webTestClient
        .get()
        .uri("/court-hearings/prisoner/qwerty")
        .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RO")))
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .json("[]")
    }
  }
  companion object {
    val UPDATED_HEARING = HmctsPcr(
      hearing = HmctsHearing(
        id = UUID.randomUUID().toString(),
        courtDetails = HmctsCourtDetails(
          court = HmctsCourt(
            courtHouseCode = "B01",
            courtHouseName = "Sheffield Crown Court",
            courtHouseId = UUID.fromString(TEST_HMCTS_COURTHOUSE_ID),
          ),
          ljaName = "South Yorkshire",
        ),
        hearingDate = LocalDate.now().plusDays(7),
        hearingType = "Second hearing",
        jurisdiction = "Crown",
        nextHearing = HmctsNextHearing(
          court = HmctsCourt(
            courtHouseCode = "B01",
            courtHouseName = "Sheffield Crown Court",
            courtHouseId = UUID.fromString(TEST_HMCTS_COURTHOUSE_ID),
          ),
          dateTime = ZonedDateTime.now().plusDays(30),
          hearingId = HmctsSubcriptionApiMockServer.TEST_HMCTS_HEARING_ID,
        ),
      ),
      offences = listOf(
        HmctsOffence(
          code = "TH68001",
          listingNumber = 1,
          offenceLegislation = "Theft Act 1968",
          pleaDate = LocalDate.now().minusDays(5),
          pleaValue = "Guilty",
          results = listOf(
            HmctsResult(
              resultTexts = listOf(
                HmctsResultText(
                  label = "Sentence",
                  value = "12 months imprisonment",
                ),
                HmctsResultText(
                  label = "Compensation",
                  value = "£500",
                ),
              ),
              resultDescription = "CSS - Custodial sentence",
            ),
          ),
          startDate = LocalDate.now().minusMonths(2),
          title = "Theft",
          wording = "On the 15th June 2026, stole property belonging to another.",
        ),
      ),
    )
  }
}
