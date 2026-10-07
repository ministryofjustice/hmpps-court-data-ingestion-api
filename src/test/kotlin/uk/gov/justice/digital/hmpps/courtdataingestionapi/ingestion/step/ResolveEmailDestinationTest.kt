package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.step

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.DeliveryCategory
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.DeliveryCategoryRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.EmailMapping
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.PrisonEmailMappingRepository
import java.util.Optional
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
    categoryCode = categoryCode,
  )

  private fun givenMapping(email: String, prisonCode: String?, categoryCode: String?, requiresPrisonCode: Boolean): EmailMapping {
    val mapping = mapping(prisonCode, categoryCode)
    whenever(repository.findMappingByEmail(email)).thenReturn(mapping)
    if (categoryCode != null) {
      whenever(categoryRepository.findById(categoryCode))
        .thenReturn(Optional.of(category(categoryCode, requiresPrisonCode)))
    }
    return mapping
  }

  private fun category(code: String, requiresPrisonCode: Boolean) = DeliveryCategory(
    code = code,
    name = code,
    requiresPrisonCode = requiresPrisonCode,
    unmatchedNeedsReview = true,
  )

  @ParameterizedTest
  @MethodSource("categories")
  fun `the addressed organisation is the category of the mapping, including the longer category codes`(
    categoryCode: String,
    prisonCode: String?,
    requiresPrisonCode: Boolean,
    expectedOrganisation: String,
  ) {
    givenMapping("mapped@example.gov.uk", prisonCode, categoryCode, requiresPrisonCode)

    val result = enricher.enrich(context("mapped@example.gov.uk"))

    assertThat(result.addressedOrganisation).isEqualTo(expectedOrganisation)
    assertThat(result.addressedPrison).isEqualTo(prisonCode)
  }

  @Test
  fun `a prison category maps to the prison on the mapping`() {
    givenMapping("omu.test@justice.gov.uk", prisonCode = "MDI", categoryCode = "PRISON", requiresPrisonCode = true)

    val result = enricher.enrich(context("omu.test@justice.gov.uk"))

    assertThat(result.addressedPrison).isEqualTo("MDI")
    assertThat(result.addressedOrganisation).isEqualTo("PRISON")
  }

  @Test
  fun `a category that does not use a prison code ignores one left on the mapping`() {
    givenMapping("ycs.warrants@justice.gov.uk", prisonCode = "WYI", categoryCode = "YOUTH_CUSTODY", requiresPrisonCode = false)

    val result = enricher.enrich(context("ycs.warrants@justice.gov.uk"))

    assertThat(result.addressedOrganisation).isEqualTo("YOUTH_CUSTODY")
    assertThat(result.addressedPrison).isNull()
  }

  @Test
  fun `a category created later from the UI is recorded as it is, with no code change`() {
    val mapping = givenMapping("court.only@justice.gov.uk", prisonCode = null, categoryCode = "COURT_ONLY", requiresPrisonCode = false)

    val result = enricher.enrich(context("court.only@justice.gov.uk"))

    assertThat(result.addressedOrganisation).isEqualTo("COURT_ONLY")
    assertThat(result.addressedPrison).isNull()
    assertThat(result.deliveryMappingId).isEqualTo(mapping.id)
  }

  @Test
  fun `the mapping category wins over the escort mailbox fallback`() {
    givenMapping("sheffieldcc@geoamey.co.uk", prisonCode = "LEI", categoryCode = "PRISON", requiresPrisonCode = true)

    val result = enricher.enrich(context("sheffieldcc@geoamey.co.uk"))

    assertThat(result.addressedOrganisation).isEqualTo("PRISON")
  }

  @Test
  fun `falls back to geoamey suffix when the delivery address is not mapped`() {
    whenever(repository.findMappingByEmail("sheffieldcc@geoamey.co.uk")).thenReturn(null)

    val result = enricher.enrich(context("sheffieldcc@geoamey.co.uk"))

    assertThat(result.addressedOrganisation).isEqualTo("PECS")
    assertThat(result.addressedPrison).isNull()
    assertThat(result.deliveryMappingId).isNull()
  }

  @Test
  fun `falls back to serco pecs suffix when the delivery address is not mapped`() {
    whenever(repository.findMappingByEmail("pecswoolwichcrown@serco.com")).thenReturn(null)

    val result = enricher.enrich(context("PECSWoolwichCrown@serco.com"))

    assertThat(result.addressedOrganisation).isEqualTo("PECS")
  }

  @Test
  fun `a serco address that is not a pecs mailbox is not treated as pecs`() {
    whenever(repository.findMappingByEmail("courts@serco.com")).thenReturn(null)

    val result = enricher.enrich(context("courts@serco.com"))

    assertThat(result.addressedOrganisation).isNull()
  }

  @Test
  fun `a mapping with no category but a prison code falls back to prison`() {
    givenMapping("legacy.omu@justice.gov.uk", prisonCode = "LEI", categoryCode = null, requiresPrisonCode = true)

    val result = enricher.enrich(context("legacy.omu@justice.gov.uk"))

    assertThat(result.addressedOrganisation).isEqualTo("PRISON")
    assertThat(result.addressedPrison).isEqualTo("LEI")
  }

  @Test
  fun `an unmapped address that is not an escort mailbox is left unclassified`() {
    whenever(repository.findMappingByEmail("nobody.knows@justice.gov.uk")).thenReturn(null)

    val result = enricher.enrich(context("nobody.knows@justice.gov.uk"))

    assertThat(result.addressedOrganisation).isNull()
    assertThat(result.addressedPrison).isNull()
    assertThat(result.deliveryMappingId).isNull()
  }

  @Test
  fun `the delivery address is matched ignoring case and surrounding whitespace`() {
    givenMapping("omu.leeds@justice.gov.uk", prisonCode = "LEI", categoryCode = "PRISON", requiresPrisonCode = true)

    val result = enricher.enrich(context("  OMU.Leeds@Justice.GOV.uk "))

    verify(repository).findMappingByEmail("omu.leeds@justice.gov.uk")
    assertThat(result.addressedOrganisation).isEqualTo("PRISON")
    assertThat(result.addressedPrison).isEqualTo("LEI")
  }

  @Test
  fun `a blank delivery address leaves the context unchanged`() {
    val original = context("   ")

    assertThat(enricher.enrich(original)).isEqualTo(original)
  }

  @Test
  fun `a classified address records the mapping, so it can be reversed`() {
    val mapping = givenMapping("omu.leeds@justice.gov.uk", prisonCode = "LEI", categoryCode = "PRISON", requiresPrisonCode = true)

    val result = enricher.enrich(context("omu.leeds@justice.gov.uk"))

    assertThat(result.deliveryMappingId).isEqualTo(mapping.id)
  }

  companion object {
    @JvmStatic
    fun categories() = listOf(
      Arguments.of("PRISON", "MDI", true, "PRISON"),
      Arguments.of("PECS", null, false, "PECS"),
      Arguments.of("PROBATION_SERVICE", null, false, "PROBATION_SERVICE"),
      Arguments.of("YOUTH_CUSTODY", null, false, "YOUTH_CUSTODY"),
      Arguments.of("MANUAL", null, false, "MANUAL"),
    )
  }
}
