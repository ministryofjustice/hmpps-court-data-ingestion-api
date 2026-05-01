package uk.gov.justice.digital.hmpps.courtdataingestionapi.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.TestUtil
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentView
import java.util.UUID

@Transactional
class CourtDocumentControllerIntTest : IntegrationTestBase() {

  @Test
  fun `View document ingested`() {
    sendSubscriptionNotification(MATCHING_CORE_PERSON)

    var courtDocument = courtDocumentRepository.findAll()[0]
    webTestClient
      .post()
      .uri("/court-document/${courtDocument.id}/view")
      .headers {
        it.contentType = MediaType.APPLICATION_JSON
      }
      .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RW")))
      .bodyValue(
        TestUtil.objectMapper().writeValueAsString(
          CourtDocumentView(
            username = "testuser",
          ),
        ),
      )
      .exchange()
      .expectStatus()
      .isOk

    courtDocument = courtDocumentRepository.findAll()[0]

    assertThat(courtDocument.courtDocumentViews).hasSize(1)
    assertThat(courtDocument.courtDocumentViews[0].username).isEqualTo("testuser")
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
            username = "testuser",
          ),
        ),
      )
      .exchange()
      .expectStatus()
      .isNotFound
  }
}
