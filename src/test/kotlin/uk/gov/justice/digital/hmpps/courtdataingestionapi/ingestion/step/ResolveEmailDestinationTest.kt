package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.step

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.DestinationType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.DeliveryCategory
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.DeliveryCategoryRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.EmailMapping
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.PrisonEmailMappingRepository
import java.util.UUID

class ResolveEmailDestinationTest {

  private val repository = mock<PrisonEmailMappingRepository>()
  private val categoryRepository = mock<DeliveryCategoryRepository>()
  private val enricher = ResolveEmailDestination(repository, categoryRepository)

  private fun context(prisonEmailAddress: String) = IngestionContext(
    prisonEmailAddress = prisonEmailAddress,
    prisonDocumentId = null,
  )

  private fun mapping(prisonCode: String?, categoryCode: String?) = EmailMapping(
    id = UUID.randomUUID(),
    email = "mapped@example.gov.uk",
    prisonCode = prisonCode,
    sourceType = categoryCode,
    categoryCode = categoryCode,
  )

  private fun category(code: String, requiresPrisonCode: Boolean) = DeliveryCategory(
    code = code,
    name = code,
    requiresPrisonCode = requiresPrisonCode,
    unmatchedNeedsReview = true,
  )

  @Test
  fun `identifies prison destination from mapping`() {
    whenever(repository.findMappingByEmail("omu.test@justice.gov.uk"))
      .thenReturn(mapping(prisonCode = "MDI", categoryCode = "PRISON"))
    whenever(categoryRepository.findByCode("PRISON")).thenReturn(category("PRISON", requiresPrisonCode = true))

    val result = enricher.enrich(context("omu.test@justice.gov.uk"))

    assertThat(result.addressedPrison).isEqualTo("MDI")
    assertThat(result.destinationType).isEqualTo(DestinationType.PRISON)
  }

  @Test
  fun `identifies pecs destination from a mapping with no prison code`() {
    whenever(repository.findMappingByEmail("pecs.south@example.gov.uk"))
      .thenReturn(mapping(prisonCode = null, categoryCode = "PECS"))
    whenever(categoryRepository.findByCode("PECS")).thenReturn(category("PECS", requiresPrisonCode = false))

    val result = enricher.enrich(context("pecs.south@example.gov.uk"))

    assertThat(result.addressedPrison).isNull()
    assertThat(result.destinationType).isEqualTo(DestinationType.PECS)
  }

  @Test
  fun `falls back to geoamey suffix when the delivery address is not mapped`() {
    whenever(repository.findMappingByEmail("sheffieldcc@geoamey.co.uk")).thenReturn(null)

    val result = enricher.enrich(context("sheffieldcc@geoamey.co.uk"))

    assertThat(result.destinationType).isEqualTo(DestinationType.PECS)
  }

  @Test
  fun `falls back to serco pecs suffix when the delivery address is not mapped`() {
    whenever(repository.findMappingByEmail("pecswoolwichcrown@serco.com")).thenReturn(null)

    val result = enricher.enrich(context("PECSWoolwichCrown@serco.com"))

    assertThat(result.destinationType).isEqualTo(DestinationType.PECS)
  }

  @Test
  fun `a category with no delivery source equivalent leaves both the prison and the source null`() {
    val mapping = mapping(prisonCode = null, categoryCode = "YOUTH_CUSTODY")
    whenever(repository.findMappingByEmail("ycs.warrants@justice.gov.uk")).thenReturn(mapping)
    whenever(categoryRepository.findByCode("YOUTH_CUSTODY"))
      .thenReturn(category("YOUTH_CUSTODY", requiresPrisonCode = false))

    val result = enricher.enrich(context("ycs.warrants@justice.gov.uk"))

    assertThat(result.addressedPrison).isNull()
    assertThat(result.destinationType).isNull()
  }

  @Test
  fun `a prison category records the mapping, so it can be reversed`() {
    val mapping = mapping(prisonCode = "LEI", categoryCode = "PRISON")
    whenever(repository.findMappingByEmail("omu.leeds@justice.gov.uk")).thenReturn(mapping)
    whenever(categoryRepository.findByCode("PRISON")).thenReturn(category("PRISON", requiresPrisonCode = true))

    val result = enricher.enrich(context("omu.leeds@justice.gov.uk"))

    assertThat(result.deliveryMappingId).isEqualTo(mapping.id)
  }
}
