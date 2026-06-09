package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.step

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionContext

class HashExtractedTextTest {

  private val enricher = HashExtractedText()

  @Test
  fun `hashes extracted text`() {
    val input = IngestionContext(
      prisonEmailAddress = null,
      prisonDocumentId = null,
      extractedText = "hello text",
      hearingId = null,
      caseReferences = null,
    )

    val result = enricher.enrich(input)

    assertThat(result.extractedTextSha256).isEqualTo("9b5338484b3ed7b9a89666b6fa71a30d8186b67a4c0933c5f11b2d1f67998ba5")
  }

  @Test
  fun `skips when no extracted text present`() {
    val input = IngestionContext(null, null, hearingId = null, caseReferences = null)

    val result = enricher.enrich(input)

    assertThat(result).isEqualTo(input)
  }
}
