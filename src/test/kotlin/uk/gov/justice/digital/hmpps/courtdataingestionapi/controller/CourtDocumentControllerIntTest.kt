package uk.gov.justice.digital.hmpps.courtdataingestionapi.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.TestUtil
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmppsCourtCasesReleaseDatesApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocument
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentView
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtHearing
import uk.gov.justice.digital.hmpps.courtdataingestionapi.typeReference
import java.util.UUID

@Transactional
class CourtDocumentControllerIntTest : IntegrationTestBase() {

  @Nested
  @DisplayName("View court document tests")
  inner class GetCourtDocumentsTests {

    @Test
    fun `Get court documents for person and document id matching`() {
      sendSubscriptionNotification(MATCHING_CORE_PERSON)
      val dbCourtDocument = courtDocumentRepository.findAll()[0]
      val documents = webTestClient
        .get()
        .uri("/court-document/person/${MATCHING_PRISONER_NUMBER}?prisonDocumentIds=${dbCourtDocument.prisonDocumentId}")
        .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RO")))
        .exchange()
        .expectBody(typeReference<List<CourtDocument>>())
        .returnResult().responseBody!!

      assertThat(documents).hasSize(1)
      assertThat(documents[0].isUnread).isTrue
      assertThat(documents[0].caseReferences).isEqualTo(listOf(CASE_REFERENCE))
      assertThat(documents[0].prisonDocumentId).isEqualTo(dbCourtDocument.prisonDocumentId)
      assertThat(documents[0].documentType).isEqualTo(CourtDocumentType.PRISON_COURT_REGISTER)
      assertThat(documents[0].courtHearing).isEqualTo(
        CourtHearing(
          "Central London County Court",
          "First hearing",
        ),
      )
    }

    @Test
    fun `Get court documents for document id matching but not person`() {
      sendSubscriptionNotification(MATCHING_CORE_PERSON)
      val dbCourtDocument = courtDocumentRepository.findAll()[0]
      val documents = webTestClient
        .get()
        .uri("/court-document/person/XYZ1234?prisonDocumentIds=${dbCourtDocument.prisonDocumentId}")
        .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RO")))
        .exchange()
        .expectBody(typeReference<List<CourtDocument>>())
        .returnResult().responseBody!!

      assertThat(documents).hasSize(0)
    }
  }

  @Nested
  @DisplayName("View court document tests")
  inner class ViewDocumentTests {
    @Test
    fun `View document ingested`() {
      sendSubscriptionNotification(MATCHING_CORE_PERSON)

      var courtDocument = courtDocumentRepository.findAll()[0]
      webTestClient
        .post()
        .uri("/court-document/${courtDocument.prisonDocumentId}/view")
        .headers {
          it.contentType = MediaType.APPLICATION_JSON
        }
        .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RW")))
        .bodyValue(
          TestUtil.objectMapper().writeValueAsString(
            CourtDocumentView(
              username = TEST_USERNAME,
            ),
          ),
        )
        .exchange()
        .expectStatus()
        .isOk

      courtDocument = courtDocumentRepository.findAll()[0]

      assertThat(courtDocument.courtDocumentViews).hasSize(1)
      assertThat(courtDocument.courtDocumentViews[0].username).isEqualTo(TEST_USERNAME)
      HmppsCourtCasesReleaseDatesApiExtension.hmppsCourtCasesReleaseDatesApi.verifyEvictCache()
    }

    @Test
    fun `View document not found`() {
      webTestClient
        .post()
        .uri("/court-document/${UUID.randomUUID()}/view")
        .headers {
          it.contentType = MediaType.APPLICATION_JSON
        }
        .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RW")))
        .bodyValue(
          TestUtil.objectMapper().writeValueAsString(
            CourtDocumentView(
              username = TEST_USERNAME,
            ),
          ),
        )
        .exchange()
        .expectStatus()
        .isNotFound
    }
  }
}
