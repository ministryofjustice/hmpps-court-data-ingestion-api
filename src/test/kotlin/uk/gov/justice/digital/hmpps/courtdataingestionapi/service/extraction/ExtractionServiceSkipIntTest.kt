package uk.gov.justice.digital.hmpps.courtdataingestionapi.service.extraction

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmppsDocumentManagementApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.ExtractionResultRepository
import java.util.UUID

@TestPropertySource(properties = ["feature-toggles.structured-extraction=true"])
class ExtractionServiceSkipIntTest : IntegrationTestBase() {

  @Autowired
  lateinit var extractionService: ExtractionService

  @Autowired lateinit var extractionResultRepository: ExtractionResultRepository

  @Test
  fun `skips a document whose bytes are not a pdf`() {
    val documentId = UUID.randomUUID()
    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi
      .stubDownloadFile(fileBytes = "PK\u0003\u0004 not a pdf".toByteArray())

    extractionService.extractAndStore(documentId)

    assertThat(extractionResultRepository.findByDocumentId(documentId).single().status)
      .isEqualTo("SKIPPED_NON_PDF")
  }

  @Test
  fun `lets a real pdf reach the parser`() {
    val documentId = UUID.randomUUID()
    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi
      .stubDownloadFile(fileBytes = "%PDF-1.7\nrest".toByteArray(Charsets.ISO_8859_1))

    extractionService.extractAndStore(documentId)

    assertThat(extractionResultRepository.findByDocumentId(documentId).single().status)
      .isNotEqualTo("SKIPPED_NON_PDF")
  }
}
