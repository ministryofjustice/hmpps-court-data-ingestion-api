package uk.gov.justice.digital.hmpps.courtdataingestionapi.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocument
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentHearing
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.typeReference
import java.time.LocalDate

@Transactional
class UnmatchedCourtDocumentControllerIntTest : IntegrationTestBase() {

  @Nested
  @DisplayName("GET unmatched court document tests")
  inner class GetUnmatchedCourtDocumentsTests {

    @Test
    fun `Get unmatched court documents for given document id`() {
      sendSubscriptionNotificationWaitForRecordToBeCreated(NO_MATCHING_IDS_PERSON)
      val dbCourtDocument = courtDocumentRepository.findAll()[0]
      val documents = webTestClient
        .get()
        .uri("/unmatched-document?prisonDocumentIds=${dbCourtDocument.prisonDocumentId}")
        .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RO")))
        .exchange()
        .expectBody(typeReference<List<CourtDocument>>())
        .returnResult().responseBody!!

      assertThat(documents).hasSize(1)
      assertThat(documents[0].isUnread).isFalse
      assertThat(documents[0].caseReferences).isEqualTo(listOf(CASE_REFERENCE))
      assertThat(documents[0].prisonDocumentId).isEqualTo(dbCourtDocument.prisonDocumentId)
      assertThat(documents[0].documentType).isEqualTo(CourtDocumentType.PRISON_COURT_REGISTER)
      assertThat(documents[0].courtHearing).isEqualTo(
        CourtDocumentHearing(
          "Central London County Court",
          "First hearing",
          LocalDate.of(2026, 6, 4),
        ),
      )
    }

    @Test
    fun `Get unmatched court documents when document id given is for a matched document instead, should return empty list`() {
      sendSubscriptionNotificationWaitForRecordToBeCreated(MATCHING_CORE_PERSON)
      val dbCourtDocument = courtDocumentRepository.findAll()[0]
      val documents = webTestClient
        .get()
        .uri("/unmatched-document?prisonDocumentIds=${dbCourtDocument.prisonDocumentId}")
        .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RW")))
        .exchange()
        .expectBody(typeReference<List<CourtDocument>>())
        .returnResult().responseBody!!

      assertThat(documents).hasSize(0)
    }

    @Test
    fun `Get unmatched court documents when no document id is given, should return empty list`() {
      val documents = webTestClient
        .get()
        .uri("/unmatched-document?prisonDocumentIds=")
        .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RO")))
        .exchange()
        .expectBody(typeReference<List<CourtDocument>>())
        .returnResult().responseBody!!

      assertThat(documents).hasSize(0)
    }
  }

  @Nested
  @DisplayName("Test Unmatched document controller Error responses")
  inner class GetUnmatchedCourtDocumentErrorTests {
    @Test
    fun `Get unmatched court documents without authorisation token being set, should return Unauthorised error`() {
      sendSubscriptionNotificationWaitForRecordToBeCreated(NO_MATCHING_IDS_PERSON)
      val dbCourtDocument = courtDocumentRepository.findAll()[0]
      webTestClient
        .get()
        .uri("/unmatched-document?prisonDocumentIds=${dbCourtDocument.prisonDocumentId}")
        .exchange()
        .expectStatus()
        .isUnauthorized
    }

    @Test
    fun `Get unmatched court documents without the right authorisation role, should return Forbidden error`() {
      sendSubscriptionNotificationWaitForRecordToBeCreated(NO_MATCHING_IDS_PERSON)
      val dbCourtDocument = courtDocumentRepository.findAll()[0]
      webTestClient
        .get()
        .uri("/unmatched-document?prisonDocumentIds=${dbCourtDocument.prisonDocumentId}")
        .headers(setAuthorisation(roles = listOf("COURTCASE_RELEASEDATE_SUPPORT")))
        .exchange()
        .expectStatus()
        .isForbidden
    }

    @Test
    fun `Get unmatched court documents for given document id`() {
      webTestClient
        .get()
        .uri("/unmatched-document")
        .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RO")))
        .exchange()
        .expectStatus()
        .is5xxServerError
    }
  }
}
