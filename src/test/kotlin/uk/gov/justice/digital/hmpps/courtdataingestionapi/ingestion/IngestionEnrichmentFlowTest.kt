package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IngestionEnrichmentFlowTest {

  @Test
  fun `applies enrichers in list order and threads the context through each`() {
    val calls = mutableListOf<String>()
    val first = IngestionEnricher { ctx ->
      calls.add("first")
      ctx.copy(addressedPrison = "A")
    }
    val second = IngestionEnricher { ctx ->
      calls.add("second")
      ctx.copy(addressedPrison = "${ctx.addressedPrison}B")
    }
    val flow = IngestionEnrichmentFlow(listOf(first, second))

    val result = flow.run(IngestionContext(prisonEmailAddress = "omu.example@example.com", prisonDocumentId = null))

    assertThat(calls).containsExactly("first", "second")
    assertThat(result.addressedPrison).isEqualTo("AB")
  }
}
