package uk.gov.justice.digital.hmpps.courtdataingestionapi.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmppsDocumentManagementApiExtension
import java.util.UUID

class HmppsDocumentManagementApiIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var documentManagementApi: HmppsDocumentManagementApi

  private val documentApiMock get() = HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi
  private val defaultBody = HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.happyDocumentFindByUuids

  @AfterEach
  fun restoreDefaultStub() {
    documentApiMock.happyDocumentFindByUuids = defaultBody
    documentApiMock.stubDocumentFindByUuids()
  }

  @Test
  fun `duplicateOf is null when the response omits the field (current fixture, reproduces the loss)`() {
    val uuid = UUID.randomUUID()

    val documents = documentManagementApi.findByDocumentUuids(listOf(uuid))

    assertThat(documents).allSatisfy { assertThat(it.duplicateOf).isNull() }
  }

  @Test
  fun `duplicateOf binds when the response includes it under that name`() {
    val original = UUID.randomUUID()
    val copy = UUID.randomUUID()
    documentApiMock.happyDocumentFindByUuids = twoDocuments(original = original, copy = copy)
    documentApiMock.stubDocumentFindByUuids()

    val byUuid = documentManagementApi.findByDocumentUuids(listOf(original, copy)).associateBy { it.documentUuid }

    assertThat(byUuid.getValue(original).duplicateOf).isNull()
    assertThat(byUuid.getValue(copy).duplicateOf).isEqualTo(original)
  }

  private fun twoDocuments(original: UUID, copy: UUID) = """
    [
      ${document(original, duplicateOf = null)},
      ${document(copy, duplicateOf = original)}
    ]
  """.trimIndent()

  private fun document(uuid: UUID, duplicateOf: UUID?) = """
    {
      "documentUuid": "$uuid",
      "documentType": "HMCTS_WARRANT",
      "documentFilename": "warrant",
      "filename": "warrant",
      "fileExtension": "pdf",
      "fileSize": 1,
      "fileHash": "raw-$uuid",
      "fileContentHash": "content-$uuid",
      "mimeType": "application/pdf",
      "metadata": { "prisonNumber": "A3242ED" },
      "createdTime": "2026-07-06T16:23:21.023327",
      "createdByServiceName": "court-data-ingestion-api",
      "createdByUsername": "TEST",
      "duplicateOf": ${if (duplicateOf == null) "null" else "\"$duplicateOf\""}
    }
  """.trimIndent()
}
