package uk.gov.justice.digital.hmpps.courtdataingestionapi.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsCourtScheduleApiExtension.Companion.hmctsCourtScheduleApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsSubcriptionApiMockServer
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtHearing
import uk.gov.justice.digital.hmpps.courtdataingestionapi.typeReference
import java.time.LocalDate
import java.util.UUID

class CourtHearingScheduleApiControllerIntTest : IntegrationTestBase() {

  @Nested
  @DisplayName("Get court hearing test")
  inner class GetCourtHearingTests {

    @Test
    fun `Get court hearing for matching hearing`() {
      hmctsCourtScheduleApi.stubCourtSchedule()
      sendSubscriptionNotification(MATCHING_CORE_PERSON)

      val hearing = getCourtHearing(MATCHING_PRISONER_NUMBER, HmctsSubcriptionApiMockServer.TEST_HMCTS_HEARING_ID)

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
    fun `Ingestion of updated court hearing`() {
      hmctsCourtScheduleApi.stubCourtSchedule()
      sendSubscriptionNotification(MATCHING_CORE_PERSON)

      var hearing = getCourtHearing(MATCHING_PRISONER_NUMBER, HmctsSubcriptionApiMockServer.TEST_HMCTS_HEARING_ID)
      assertThat(hearing.hearingType).isEqualTo("First hearing")

      hmctsCourtScheduleApi.stubCourtSchedule("Second hearing")
      sendSubscriptionNotification(MATCHING_CORE_PERSON)
      hearing = getCourtHearing(MATCHING_PRISONER_NUMBER, HmctsSubcriptionApiMockServer.TEST_HMCTS_HEARING_ID)
      assertThat(hearing.hearingType).isEqualTo("Second hearing")
    }

    @Test
    fun `Get court hearing given a hearing with no matching court in register, then return matching hearing and null courtCode`() {
      hmctsCourtScheduleApi.stubCourtScheduleWithoutCourtRegistry()
      sendSubscriptionNotification(MATCHING_CORE_PERSON)

      val hearing = getCourtHearing(MATCHING_PRISONER_NUMBER, HmctsSubcriptionApiMockServer.TEST_HMCTS_HEARING_ID)

      assertThat(hearing.hearingId).isEqualTo(UUID.fromString(HmctsSubcriptionApiMockServer.TEST_HMCTS_HEARING_ID))
      assertThat(hearing.courtName).isEqualTo("Central London County Court")
      assertThat(hearing.courtId).isEqualTo(UUID.fromString("f2d1bad6-0333-485b-a6ca-7d01a8804dc7"))
      assertThat(hearing.courtCode).isNull()
      assertThat(hearing.hearingDate).isEqualTo(LocalDate.of(2026, 6, 4))
      assertThat(hearing.caseReferences).isEqualTo(listOf("CASE123456"))
      assertThat(hearing.hearingType).isEqualTo("First hearing")
      assertThat(hearing.documents.size).isEqualTo(1)
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
}
