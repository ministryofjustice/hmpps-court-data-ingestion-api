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
import java.time.LocalDateTime
import java.util.UUID

class CourtHearingControllerIntTest : IntegrationTestBase() {

  @Nested
  @DisplayName("Get court hearing test")
  inner class GetCourtHearingTests {

    @Test
    fun `Get court hearing for matching hearing`() {
      hmctsCourtScheduleApi.stubCourtSchedule()
      sendSubscriptionNotification(MATCHING_CORE_PERSON)

      val hearing = requestCourtHearingBtHmctsHearingId()

      assertThat(hearing.hearingId).isEqualTo(UUID.fromString(HmctsSubcriptionApiMockServer.TEST_HMCTS_HEARING_ID))
      assertThat(hearing.courtName).isEqualTo("Central London County Court")
      assertThat(hearing.courtId).isEqualTo(UUID.fromString("e2d1bad5-0222-485a-a6ca-6d01a8804db6"))
      assertThat(hearing.courtCode).isEqualTo("LND001")
      assertThat(hearing.hearingDate).isEqualTo(LocalDateTime.of(2026, 6, 4, 11, 0, 0))
      assertThat(hearing.caseReferences).isEqualTo(listOf("CASE123456"))
      assertThat(hearing.hearingType).isEqualTo("First hearing")
      assertThat(hearing.documents.size).isEqualTo(1)
    }

    @Test
    fun `Get court hearing given a hearing with no matching court in register, then return matching hearing and null courtCode`() {
      hmctsCourtScheduleApi.stubCourtScheduleWithoutCourtRegistry()
      sendSubscriptionNotification(MATCHING_CORE_PERSON)

      val hearing = requestCourtHearingBtHmctsHearingId()

      assertThat(hearing.hearingId).isEqualTo(UUID.fromString(HmctsSubcriptionApiMockServer.TEST_HMCTS_HEARING_ID))
      assertThat(hearing.courtName).isEqualTo("Central London County Court")
      assertThat(hearing.courtId).isEqualTo(UUID.fromString("f2d1bad6-0333-485b-a6ca-7d01a8804dc7"))
      assertThat(hearing.courtCode).isNull()
      assertThat(hearing.hearingDate).isEqualTo(LocalDateTime.of(2026, 6, 4, 11, 0, 0))
      assertThat(hearing.caseReferences).isEqualTo(listOf("CASE123456"))
      assertThat(hearing.hearingType).isEqualTo("First hearing")
      assertThat(hearing.documents.size).isEqualTo(1)
    }

    @Test
    fun `Get court hearing for not found hearing`() {
      webTestClient
        .get()
        .uri("/court-hearings/${UUID.randomUUID()}")
        .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RO")))
        .exchange()
        .expectStatus()
        .isNotFound
    }

    private fun requestCourtHearingBtHmctsHearingId(): CourtHearing = webTestClient
      .get()
      .uri("/court-hearings/${HmctsSubcriptionApiMockServer.TEST_HMCTS_HEARING_ID}")
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
      assertThat(hearing.hearingDate).isEqualTo(LocalDateTime.of(2026, 6, 4, 11, 0, 0))
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
