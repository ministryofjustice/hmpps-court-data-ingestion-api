package uk.gov.justice.digital.hmpps.courtdataingestionapi.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsSubcriptionApiMockServer
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtHearing
import java.time.LocalDateTime
import java.util.UUID

@Transactional
class CourtHearingControllerIntTest : IntegrationTestBase() {

  @Nested
  @DisplayName("Get court hearing test")
  inner class GetCourtHearingTests {

    @Test
    fun `Get court hearing for matching hearing`() {
      sendSubscriptionNotification(MATCHING_CORE_PERSON)
      val hearing = webTestClient
        .get()
        .uri("/court-hearing/${HmctsSubcriptionApiMockServer.TEST_HMCTS_HEARING_ID}")
        .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RO")))
        .exchange()
        .expectBody(CourtHearing::class.java)
        .returnResult().responseBody!!

      assertThat(hearing.hearingId).isEqualTo(UUID.fromString(HmctsSubcriptionApiMockServer.TEST_HMCTS_HEARING_ID))
      assertThat(hearing.courtName).isEqualTo("Central London County Court")
      assertThat(hearing.courtId).isEqualTo(UUID.fromString("e2d1bad5-0222-485a-a6ca-6d01a8804db6"))
      assertThat(hearing.hearingDate).isEqualTo(LocalDateTime.of(2026, 6, 4, 11, 0, 0))
      assertThat(hearing.caseReferences).isEqualTo(listOf("CASE123456"))
      assertThat(hearing.hearingType).isEqualTo("First hearing")
      assertThat(hearing.documents.size).isEqualTo(1)
    }

    @Test
    fun `View court hearing not found`() {
      webTestClient
        .get()
        .uri("/court-hearing/${UUID.randomUUID()}")
        .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RO")))
        .exchange()
        .expectStatus()
        .isNotFound
    }
  }
}
