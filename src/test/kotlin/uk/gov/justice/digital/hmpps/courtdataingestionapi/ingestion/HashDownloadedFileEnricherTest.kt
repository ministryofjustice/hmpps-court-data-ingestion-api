package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HashDownloadedFileEnricherTest {

  private val enricher = HashDownloadedFileEnricher()

  @Test
  fun `hashes downloaded file bytes`() {
    val input = IngestionContext(
      prisonEmailAddress = null,
      prisonDocumentId = null,
      downloadedFileBytes = "abc".toByteArray(),
    )

    val result = enricher.enrich(input)

    assertThat(result.downloadedFileSha256).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
  }

  @Test
  fun `skips when no downloaded bytes present`() {
    val input = IngestionContext(null, null)

    val result = enricher.enrich(input)

    assertThat(result).isEqualTo(input)
  }
}
