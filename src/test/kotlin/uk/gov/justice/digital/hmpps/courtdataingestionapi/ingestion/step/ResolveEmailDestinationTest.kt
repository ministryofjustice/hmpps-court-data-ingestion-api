package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.step

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.DestinationType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.EmailMapping
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.PrisonEmailMappingRepository

class ResolveEmailDestinationTest {

  private val repository = mock<PrisonEmailMappingRepository>()
  private val enricher = ResolveEmailDestination(repository)

  @Test
  fun `identifies prison destination from mapping`() {
    whenever(repository.findMappingByEmail("omu.test@justice.gov.uk"))
      .thenReturn(EmailMapping(prisonCode = "MDI", sourceType = "PRISON"))

    val input = IngestionContext(
      prisonEmailAddress = "omu.test@justice.gov.uk",
      prisonDocumentId = null,
    )

    val result = enricher.enrich(input)

    assertThat(result.addressedPrison).isEqualTo("MDI")
    assertThat(result.destinationType).isEqualTo(DestinationType.PRISON)
  }

  @Test
  fun `identifies pecs destination from a mapping with no prison code`() {
    whenever(repository.findMappingByEmail("pecs.south@example.gov.uk"))
      .thenReturn(EmailMapping(prisonCode = null, sourceType = "PECS"))

    val input = IngestionContext(
      prisonEmailAddress = "pecs.south@example.gov.uk",
      prisonDocumentId = null,
    )

    val result = enricher.enrich(input)

    assertThat(result.addressedPrison).isNull()
    assertThat(result.destinationType).isEqualTo(DestinationType.PECS)
  }

  @Test
  fun `falls back to geoamey suffix when the mailbox is not mapped`() {
    whenever(repository.findMappingByEmail("sheffieldcc@geoamey.co.uk")).thenReturn(null)

    val input = IngestionContext(
      prisonEmailAddress = "sheffieldcc@geoamey.co.uk",
      prisonDocumentId = null,
    )

    val result = enricher.enrich(input)

    assertThat(result.destinationType).isEqualTo(DestinationType.PECS)
  }

  @Test
  fun `falls back to serco pecs suffix when the mailbox is not mapped`() {
    whenever(repository.findMappingByEmail("pecswoolwichcrown@serco.com")).thenReturn(null)

    val input = IngestionContext(
      prisonEmailAddress = "PECSWoolwichCrown@serco.com",
      prisonDocumentId = null,
    )

    val result = enricher.enrich(input)

    assertThat(result.destinationType).isEqualTo(DestinationType.PECS)
  }
}
