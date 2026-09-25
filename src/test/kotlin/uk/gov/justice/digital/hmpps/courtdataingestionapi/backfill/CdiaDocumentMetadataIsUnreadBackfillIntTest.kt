package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.module.kotlin.jacksonObjectMapper
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.PrisonDocNotificationConfigEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmppsDocumentManagementApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.Document
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentApiType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentSearchResult
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.PrisonDocNotificationConfigRepository
import java.time.LocalDateTime
import java.util.UUID

class CdiaDocumentMetadataIsUnreadBackfillIntTest : IntegrationTestBase() {

  @Autowired
  lateinit var notificationConfigRepository: PrisonDocNotificationConfigRepository

  @BeforeEach
  fun setup() {
    courtDocumentRepository.deleteAll()
    notificationConfigRepository.deleteAll()
  }

  @AfterEach
  fun resetSetup() {
    notificationConfigRepository.deleteAll()
  }

  @ParameterizedTest
  @MethodSource("getRunBackfillTestParameters")
  fun `Documents related to a prison with no date set, should NOT be updated and remain as isUnread TRUE`(newDocumentDateFrom: LocalDateTime?, expected: Int) {
    // Setup
    sendSubscriptionNotificationWaitForRecordToBeCreated(MATCHING_CORE_PERSON)
    val unreadCourtDocument = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(MATCHING_CORE_PERSON)!!
    val unreadDocument = copyDocument(unreadCourtDocument.prisonDocumentId)

    sendSubscriptionNotificationWaitForRecordToBeCreated(MATCHING_CORE_PERSON)
    val readCourtDocument = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(MATCHING_CORE_PERSON)!!
    val readDocument = copyDocument(readCourtDocument.prisonDocumentId, true)

    val pageOneResults = mutableListOf(unreadDocument, readDocument)
    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.stubFacetSearch(
      0,
      objectMapper.writeValueAsString(
        DocumentSearchResult(
          pageOneResults,
          totalResultsCount = 2,
        ),
      ),
    )
    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.stubMergeMetadata(readDocument.documentUuid)

    newDocumentDateFrom?.let {
      notificationConfigRepository.save(
        PrisonDocNotificationConfigEntity(MATCHING_PRISON_ID, it),
      )
    }

    // Run
    runBackfill("cdia-document-is-unread")

    // Check results
    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.verifyMergeMetadata(expected, readDocument.documentUuid.toString(), mapOf("isUnread" to false))
  }

  @Test
  fun `Unread documents found on DMA but not in CDIA, should NOT be updated and remain as isUnread TRUE`() {
    // Setup
    val document = copyDocument()
    val pageOneResults = mutableListOf(document)
    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.stubSearch(
      0,
      objectMapper.writeValueAsString(
        DocumentSearchResult(
          pageOneResults,
          totalResultsCount = 1,
        ),
      ),
    )

    // Run
    runBackfill("cdia-document-is-unread")

    // Check results
    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.verifyMergeMetadata(0, document.documentUuid.toString(), mapOf("isUnread" to false))
  }

  companion object {
    const val MATCHING_PRISON_ID: String = "Mock01"
    private val objectMapper = jacksonObjectMapper()

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
          "prisonerId" to MATCHING_PRISONER_NUMBER,
          "isUnread" to true,
        ),
      ),
      createdTime = LocalDateTime.now(),
      createdByServiceName = "My Service",
      createdByUsername = "My user",
      duplicateOf = null,
    )

    private fun copyDocument(documentUuid: UUID = UUID.randomUUID(), isUnread: Boolean = true): Document {
      val document: Document = document.copy(documentUuid = documentUuid)

      if (document.metadata["isUnread"].asBoolean() != isUnread) {
        document.metadata = objectMapper.valueToTree(
          mapOf(
            "source" to HmppsDocumentManagementApi.COURT_DATA_DOCUMENT_SOURCE,
            "status" to "LIVE",
            "prisonerId" to MATCHING_PRISONER_NUMBER,
            "isUnread" to isUnread,
          ),
        )
      }

      return document
    }

    @JvmStatic
    fun getRunBackfillTestParameters() = listOf(
      Arguments.of(null, 0),
      Arguments.of(LocalDateTime.now(), 0),
      Arguments.of(LocalDateTime.now().minusDays(1), 0),
      Arguments.of(LocalDateTime.now().plusDays(1), 1),
    )
  }
}
