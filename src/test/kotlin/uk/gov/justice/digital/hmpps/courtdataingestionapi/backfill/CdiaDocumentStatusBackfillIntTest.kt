package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.expectBody
import tools.jackson.module.kotlin.jacksonObjectMapper
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.controller.BackfillEndpoint
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmppsDocumentManagementApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.Document
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentApiType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentSearchResult
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtCaseDefendantService
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.DefendantMatchingService
import java.time.LocalDateTime
import java.util.UUID

class CdiaDocumentStatusBackfillIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var defendantMatchingService: DefendantMatchingService

  @Autowired
  private lateinit var courtCaseDefendantService: CourtCaseDefendantService

  @Test
  fun `Cdia documents are backfilled`() {
    // Setup
    val documentWithLiveStatus = aDocument()
    val pageOneResults = mutableListOf(documentWithLiveStatus)
    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.stubSearch(
      0,
      objectMapper.writeValueAsString(
        DocumentSearchResult(
          pageOneResults,
          totalResultsCount = 1,
        ),
      ),
    )
    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.stubMergeMetadata(documentWithLiveStatus.documentUuid)

    // Run
    startBackfill()
    backfillCallBack()

    // Check results
    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.verifyMergeMetadata(1, documentWithLiveStatus.documentUuid.toString(), mapOf("status" to "ACTIVE"))
  }

  @Test
  fun `Cdia documents none need to be backfilled`() {
    // Setup
    val documentWithLiveStatus = aDocument()
    val pageOneResults = mutableListOf<Document>()
    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.stubSearch(
      0,
      objectMapper.writeValueAsString(
        DocumentSearchResult(
          pageOneResults,
          totalResultsCount = 0,
        ),
      ),
    )
    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.stubMergeMetadata(documentWithLiveStatus.documentUuid)

    // Run
    startBackfill()
    backfillCallBack()

    // Check results
    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.verifyMergeMetadata(0, documentWithLiveStatus.documentUuid.toString())
  }

  private fun startBackfill() {
    webTestClient.post()
      .uri("/backfill")
      .bodyValue(BackfillBody("cdia-document-status"))
      .exchange()
      .expectStatus()
      .isOk
  }

  private fun backfillCallBack() {
    await untilCallTo {
      webTestClient.get()
        .uri("/backfill/cdia-document-status")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<BackfillEndpoint.StatusResponse>()
        .returnResult().responseBody!!
    } matches { it?.status == "COMPLETED" }
  }

  companion object {
    private val objectMapper = jacksonObjectMapper()
    private fun aDocument(): Document = document.copy(documentUuid = UUID.randomUUID())
    private val document: Document = Document(
      documentUuid = UUID.randomUUID(),
      documentType = DocumentApiType.HMCTS_WARRANT,
      documentFilename = "filename.pdf",
      filename = "filename",
      fileExtension = "pdf",
      fileSize = 1,
      fileHash = "hash",
      fileContentHash = "content-hash",
      mimeType = "application/pdf",
      metadata = objectMapper.valueToTree(
        mapOf(
          "source" to HmppsDocumentManagementApi.COURT_DATA_DOCUMENT_SOURCE,
          "status" to "LIVE",
        ),
      ),
      createdTime = LocalDateTime.now(),
      createdByServiceName = "My Service",
      createdByUsername = "My user",
      duplicateOf = null,
    )
  }
}
