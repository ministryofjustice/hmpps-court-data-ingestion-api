//package uk.gov.justice.digital.hmpps.courtdataingestionapi.controller
//
//import org.assertj.core.api.Assertions.assertThat
//import org.junit.jupiter.api.DisplayName
//import org.junit.jupiter.api.Nested
//import org.junit.jupiter.api.Test
//import org.slf4j.LoggerFactory
//import org.springframework.data.domain.PageRequest
//import org.springframework.data.domain.Sort.Direction
//import org.springframework.http.MediaType
//import org.springframework.test.web.reactive.server.returnResult
//import org.springframework.transaction.annotation.Transactional
//import uk.gov.justice.digital.hmpps.courtdataingestionapi.TestUtil
//import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
//import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmppsCourtCasesReleaseDatesApiExtension
//import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocument
//import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentView
////import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentSearchOrderBy
////import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentSearchRequest
//import uk.gov.justice.digital.hmpps.courtdataingestionapi.typeReference
//import java.util.UUID
//
//@Transactional
//class UnmatchedDocumentControllerIntTest : IntegrationTestBase() {
//
//  @Nested
//  @DisplayName("View unmatched document tests")
//  inner class GetCourtDocumentsTests {
//
//    @Test
//    fun `Get unmatched court documents for person and document id matching`() {
//      sendSubscriptionNotification(MATCHING_CORE_PERSON)
//      sendSubscriptionNotification(NOT_FOUND_CORE_PERSON)
//
//      val dbCourtDocuments = courtDocumentRepository.findAll()
//      val dbCourtDocument = courtDocumentRepository.findAll()[0]
//
//      log.info("Total docs found {}", dbCourtDocuments.size)
//      val searchPageRequest = DocumentSearchRequest(null,)
//
//      val documents = webTestClient
//        .post()
//        .uri("/unmatched-documents/list")
//        .headers {
//          it.contentType = MediaType.APPLICATION_JSON
//        }
//        .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RO")))
////        .bodyValue(DocumentSearchRequest(null, 0, 20, DocumentSearchOrderBy.CREATED_TIME, Direction.DESC))
////        .bodyValue(DocumentSearchRequest(null, null, null, null, null))null
//        .bodyValue(DocumentSearchRequest(null,))
//        .exchange()
//        .expectBody(typeReference<List<CourtDocument>>())
////        .expectBody(typeReference<Long>())
//        .returnResult().responseBody!!
//
//      assertThat(documents.size.toString()).isEqualTo("1")
//      assertThat(documents).hasSize(1)
//
////      assertThat(documents).hasSize(1)
////      assertThat(documents[0].isUnread).isTrue
////      assertThat(documents[0].caseReferences).isEqualTo(listOf("Case123", "Case456"))
////      assertThat(documents[0].prisonDocumentId).isEqualTo(dbCourtDocument.prisonDocumentId)
//    }
//
////    @Test
////    fun `Get court documents for document id matching but not person`() {
////      sendSubscriptionNotification(MATCHING_CORE_PERSON)
////      val dbCourtDocument = courtDocumentRepository.findAll()[0]
////      val documents = webTestClient
////        .get()
////        .uri("/court-document/person/XYZ1234?prisonDocumentIds=${dbCourtDocument.prisonDocumentId}")
////        .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RO")))
////        .exchange()
////        .expectBody(typeReference<List<CourtDocument>>())
////        .returnResult().responseBody!!
////
////      assertThat(documents).hasSize(0)
////    }
////  }
////
////  @Nested
////  @DisplayName("View court document tests")
////  inner class ViewDocumentTests {
////    @Test
////    fun `View document ingested`() {
////      sendSubscriptionNotification(MATCHING_CORE_PERSON)
////
////      var courtDocument = courtDocumentRepository.findAll()[0]
////      webTestClient
////        .post()
////        .uri("/court-document/${courtDocument.prisonDocumentId}/view")
////        .headers {
////          it.contentType = MediaType.APPLICATION_JSON
////        }
////        .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RW")))
////        .bodyValue(
////          TestUtil.objectMapper().writeValueAsString(
////            CourtDocumentView(
////              username = "testuser",
////            ),
////          ),
////        )
////        .exchange()
////        .expectStatus()
////        .isOk
////
////      courtDocument = courtDocumentRepository.findAll()[0]
////
////      assertThat(courtDocument.courtDocumentViews).hasSize(1)
////      assertThat(courtDocument.courtDocumentViews[0].username).isEqualTo("testuser")
////      HmppsCourtCasesReleaseDatesApiExtension.hmppsCourtCasesReleaseDatesApi.verifyEvictCache()
////    }
//
////    @Test
////    fun `View document not found`() {
////      webTestClient
////        .post()
////        .uri("/court-document/${UUID.randomUUID()}/view")
////        .headers {
////          it.contentType = MediaType.APPLICATION_JSON
////        }
////        .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RW")))
////        .bodyValue(
////          TestUtil.objectMapper().writeValueAsString(
////            CourtDocumentView(
////              username = "testuser",
////            ),
////          ),
////        )
////        .exchange()
////        .expectStatus()
////        .isNotFound
////    }
//  }
//
//  companion object {
//    private val log = LoggerFactory.getLogger(this::class.java)
//  }
//}
