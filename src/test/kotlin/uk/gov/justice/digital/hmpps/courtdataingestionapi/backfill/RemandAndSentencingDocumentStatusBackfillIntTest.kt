package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.module.kotlin.jacksonObjectMapper
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmppsDocumentManagementApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.Document
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentApiType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentMetadataStatus
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentSearchResult
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtCaseDefendantService
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.DefendantMatchingService
import java.time.LocalDateTime
import java.util.UUID

class RemandAndSentencingDocumentStatusBackfillIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var defendantMatchingService: DefendantMatchingService

  @Autowired
  private lateinit var courtCaseDefendantService: CourtCaseDefendantService

  @Test
  fun `Remand and sentencing documents are backfilled`() {
    val aRasDocumentWithCorrectStatuses = aDocument().copy(
      metadata = objectMapper.valueToTree(
        mapOf(
          "source" to "RemandSentencingUser",
          "status" to DocumentMetadataStatus.AWAITING.name,
        ),
      ),
    )
    val aRasDocumentWithOldStatuses = aDocument().copy(
      metadata = objectMapper.valueToTree(
        mapOf(
          "source" to "RemandSentencingUser",
          "status" to "Deleted",
        ),
      ),
    )
    val aRasDocumentWithNoStatus = aDocument().copy(
      metadata = objectMapper.valueToTree(
        mapOf(
          "source" to "RemandSentencingUser",
        ),
      ),
    )
    val aCdiaDocument = aDocument()
    val pageOneResults = mutableListOf<Document>()
    pageOneResults.add(aRasDocumentWithCorrectStatuses)
    pageOneResults.add(aRasDocumentWithOldStatuses)
    pageOneResults.add(aRasDocumentWithNoStatus)
    pageOneResults.add(aCdiaDocument)
    repeat(196) {
      pageOneResults.add(aDocument())
    }
    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.stubSearch(
      0,
      objectMapper.writeValueAsString(
        DocumentSearchResult(
          pageOneResults,
          totalResultsCount = 201,
        ),
      ),
    )
    val pageTwoResults = listOf(aDocument())
    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.stubSearch(
      1,
      objectMapper.writeValueAsString(
        DocumentSearchResult(
          pageTwoResults,
          totalResultsCount = 201,
        ),
      ),
    )
    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.stubSearch(
      2,
      objectMapper.writeValueAsString(
        DocumentSearchResult(
          emptyList(),
          totalResultsCount = 201,
        ),
      ),
    )

    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.stubMergeMetadata(aRasDocumentWithOldStatuses.documentUuid)
    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.stubMergeMetadata(aRasDocumentWithNoStatus.documentUuid)

    runBackfill("remand-and-sentencing-document-status")

    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.verifyMergeMetadata(1, aRasDocumentWithOldStatuses.documentUuid.toString(), mapOf("status" to "DELETED"))
    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.verifyMergeMetadata(1, aRasDocumentWithNoStatus.documentUuid.toString(), mapOf("status" to "ACTIVE"))
    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.verifyMergeMetadata(0, aRasDocumentWithCorrectStatuses.documentUuid.toString())
    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.verifyMergeMetadata(0, aCdiaDocument.documentUuid.toString())
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
      metadata = objectMapper.valueToTree(mapOf("source" to HmppsDocumentManagementApi.COURT_DATA_DOCUMENT_SOURCE)),
      createdTime = LocalDateTime.now(),
      createdByServiceName = "My Service",
      createdByUsername = "My user",
      duplicateOf = null,
    )
  }
}

data class BackfillBody(val id: String)
