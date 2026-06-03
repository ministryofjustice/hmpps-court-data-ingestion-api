package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.PrisonEmailMappingRepository

class IdentifyDestinationEnricherTest {

  private val repository = mock<PrisonEmailMappingRepository>()
  private val enricher = IdentifyDestinationEnricher(repository)

  @Test
  fun `identifies prison destination from mapping`() {
    whenever(repository.findPrisonCodeByEmail("omu.test@justice.gov.uk")).thenReturn("MDI")

    val input = IngestionContext(
      prisonEmailAddress = "omu.test@justice.gov.uk",
      prisonDocumentId = null,
    )

    val result = enricher.enrich(input)

    assertThat(result.addressedPrison).isEqualTo("MDI")
    assertThat(result.destinationType).isEqualTo(DestinationType.PRISON)
  }

  @Test
  fun `identifies pecs destination for geoamey address`() {
    val input = IngestionContext(
      prisonEmailAddress = "sheffieldcc@geoamey.co.uk",
      prisonDocumentId = null,
    )

    val result = enricher.enrich(input)

    assertThat(result.destinationType).isEqualTo(DestinationType.PECS)
  }

  @Test
  fun `identifies pecs destination for serco pecs address`() {
    val input = IngestionContext(
      prisonEmailAddress = "PECSWoolwichCrown@serco.com",
      prisonDocumentId = null,
    )

    val result = enricher.enrich(input)

    assertThat(result.destinationType).isEqualTo(DestinationType.PECS)
  }
}
