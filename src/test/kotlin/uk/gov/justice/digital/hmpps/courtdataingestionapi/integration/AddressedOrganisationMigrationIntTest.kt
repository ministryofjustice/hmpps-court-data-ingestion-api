package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import java.time.LocalDateTime
import java.util.UUID

class AddressedOrganisationMigrationIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var jdbcTemplate: JdbcTemplate

  @Autowired
  override lateinit var courtDocumentRepository: CourtDocumentRepository

  @BeforeEach
  fun setUp() {
    courtDocumentRepository.deleteAll()
    jdbcTemplate.update("DELETE FROM prison_email_mapping")
    insertCategory("PROBATION_SERVICE")
    insertCategory("YOUTH_CUSTODY")
    insertCategory("MANUAL")
    insertCategory("COURT_ONLY")
  }

  @Test
  fun `classifies an unclassified document from the category and prison of its mapping`() {
    insertMapping("omu.leeds@justice.gov.uk", prisonCode = "LEI", categoryCode = "PRISON")
    val id = insertDocument("omu.leeds@justice.gov.uk")

    classify()

    assertThat(organisationOf(id)).isEqualTo("PRISON")
    assertThat(prisonOf(id)).isEqualTo("LEI")
    assertThat(mappingOf(id)).isEqualTo(mappingIdOf("omu.leeds@justice.gov.uk"))
    assertThat(metadataVersionOf(id)).isEqualTo(3)
  }

  @ParameterizedTest
  @ValueSource(strings = ["PECS", "PROBATION_SERVICE", "YOUTH_CUSTODY", "MANUAL"])
  fun `classifies every category that does not use a prison, including the longer codes`(categoryCode: String) {
    insertMapping("mailbox@justice.gov.uk", prisonCode = null, categoryCode = categoryCode)
    val id = insertDocument("mailbox@justice.gov.uk")

    classify()

    assertThat(organisationOf(id)).isEqualTo(categoryCode)
    assertThat(prisonOf(id)).isNull()
  }

  @Test
  fun `a category that does not use a prison ignores a prison code left on the mapping`() {
    insertMapping("ycs.warrants@justice.gov.uk", prisonCode = "WYI", categoryCode = "YOUTH_CUSTODY")
    val id = insertDocument("ycs.warrants@justice.gov.uk")

    classify()

    assertThat(organisationOf(id)).isEqualTo("YOUTH_CUSTODY")
    assertThat(prisonOf(id)).isNull()
  }

  @Test
  fun `matches the delivery address ignoring case and surrounding spaces on either side`() {
    insertMapping("omu.leeds@justice.gov.uk", prisonCode = "LEI", categoryCode = "PRISON")
    insertMapping("  Mixed.Case@Justice.GOV.uk ", prisonCode = "MDI", categoryCode = "PRISON")
    val messyDocument = insertDocument("  OMU.Leeds@Justice.gov.UK  ")
    val messyMapping = insertDocument("mixed.case@justice.gov.uk")

    classify()

    assertThat(prisonOf(messyDocument)).isEqualTo("LEI")
    assertThat(prisonOf(messyMapping)).isEqualTo("MDI")
  }

  @Test
  fun `leaves a document with an existing organisation unchanged`() {
    insertMapping("omu.leeds@justice.gov.uk", prisonCode = "LEI", categoryCode = "PRISON")
    val id = insertDocument("omu.leeds@justice.gov.uk", organisation = "PECS")

    classify()

    assertThat(organisationOf(id)).isEqualTo("PECS")
    assertThat(prisonOf(id)).isNull()
    assertThat(mappingOf(id)).isNull()
    assertThat(metadataVersionOf(id)).isEqualTo(3)
  }

  @Test
  fun `leaves a document with an existing prison unchanged`() {
    insertMapping("omu.leeds@justice.gov.uk", prisonCode = "LEI", categoryCode = "PRISON")
    val id = insertDocument("omu.leeds@justice.gov.uk", prison = "BRI")

    classify()

    assertThat(organisationOf(id)).isNull()
    assertThat(prisonOf(id)).isEqualTo("BRI")
    assertThat(metadataVersionOf(id)).isEqualTo(3)
  }

  @Test
  fun `leaves a fully classified document unchanged`() {
    insertMapping("ycs.warrants@justice.gov.uk", prisonCode = null, categoryCode = "YOUTH_CUSTODY")
    val id = insertDocument("ycs.warrants@justice.gov.uk", organisation = "PRISON", prison = "LEI")

    classify()

    assertThat(organisationOf(id)).isEqualTo("PRISON")
    assertThat(prisonOf(id)).isEqualTo("LEI")
  }

  @Test
  fun `leaves a document with no matching mapping unchanged`() {
    insertMapping("omu.leeds@justice.gov.uk", prisonCode = "LEI", categoryCode = "PRISON")
    val id = insertDocument("nobody.knows@justice.gov.uk")

    classify()

    assertThat(organisationOf(id)).isNull()
    assertThat(prisonOf(id)).isNull()
    assertThat(mappingOf(id)).isNull()
    assertThat(metadataVersionOf(id)).isEqualTo(3)
  }

  @Test
  fun `does not choose between mappings that normalise to the same address`() {
    insertMapping("dup@justice.gov.uk", prisonCode = "LEI", categoryCode = "PRISON")
    insertMapping("DUP@justice.gov.uk ", prisonCode = "MDI", categoryCode = "PRISON")
    val id = insertDocument("dup@justice.gov.uk")

    classify()

    assertThat(organisationOf(id)).isNull()
    assertThat(prisonOf(id)).isNull()
    assertThat(mappingOf(id)).isNull()
  }

  @Test
  fun `classifies a category created later from the UI, with no code change`() {
    insertMapping("court.only@justice.gov.uk", prisonCode = null, categoryCode = "COURT_ONLY")
    val id = insertDocument("court.only@justice.gov.uk")

    classify()

    assertThat(organisationOf(id)).isEqualTo("COURT_ONLY")
    assertThat(mappingOf(id)).isEqualTo(mappingIdOf("court.only@justice.gov.uk"))
  }

  @Test
  fun `leaves the metadata version alone, for the later metadata update to pick up`() {
    insertMapping("ycs.warrants@justice.gov.uk", prisonCode = null, categoryCode = "YOUTH_CUSTODY")
    val id = insertDocument("ycs.warrants@justice.gov.uk")

    classify()

    assertThat(organisationOf(id)).isEqualTo("YOUTH_CUSTODY")
    assertThat(metadataVersionOf(id)).isEqualTo(3)
  }

  @Test
  fun `keeps a classification filled in by hand`() {
    insertMapping("ycs.warrants@justice.gov.uk", prisonCode = null, categoryCode = "YOUTH_CUSTODY")
    val id = insertDocument("ycs.warrants@justice.gov.uk", organisation = "PROBATION_SERVICE")

    classify()

    assertThat(organisationOf(id)).isEqualTo("PROBATION_SERVICE")
    assertThat(mappingOf(id)).isNull()
  }

  @Test
  fun `running the classification again changes nothing`() {
    insertMapping("ycs.warrants@justice.gov.uk", prisonCode = null, categoryCode = "YOUTH_CUSTODY")
    val id = insertDocument("ycs.warrants@justice.gov.uk")
    classify()
    jdbcTemplate.update("UPDATE prison_email_mapping SET category_code = 'PROBATION_SERVICE' WHERE email = ?", "ycs.warrants@justice.gov.uk")

    classify()

    assertThat(organisationOf(id)).isEqualTo("YOUTH_CUSTODY")
  }

  @ParameterizedTest
  @ValueSource(strings = ["PRISON", "PECS", "PROBATION_SERVICE", "YOUTH_CUSTODY", "MANUAL", "COURT_ONLY"])
  fun `the column accepts every delivery category`(organisation: String) {
    val id = insertDocument("mailbox@justice.gov.uk", organisation = organisation)

    assertThat(organisationOf(id)).isEqualTo(organisation)
  }

  @Test
  fun `the column accepts no organisation`() {
    val id = insertDocument("mailbox@justice.gov.uk", organisation = null)

    assertThat(organisationOf(id)).isNull()
  }

  @Test
  fun `the column rejects a code that is not a delivery category`() {
    assertThatThrownBy { insertDocument("mailbox@justice.gov.uk", organisation = "NOT_A_CATEGORY") }
      .isInstanceOf(DataIntegrityViolationException::class.java)
      .hasMessageContaining("fk_court_document_addressed_organisation")
  }

  private fun classify() {
    jdbcTemplate.execute(
      ClassPathResource("migration/postgres/V43__classify_unaddressed_documents.sql").getContentAsString(Charsets.UTF_8),
    )
  }

  private fun insertCategory(code: String) = jdbcTemplate.update(
    """
      INSERT INTO delivery_category (code, name, requires_prison_code, unmatched_needs_review)
      VALUES (?, ?, FALSE, TRUE)
      ON CONFLICT (code) DO NOTHING
    """.trimIndent(),
    code,
    code,
  )

  private fun insertMapping(email: String, prisonCode: String?, categoryCode: String) = jdbcTemplate.update(
    "INSERT INTO prison_email_mapping (email, prison_code, category_code) VALUES (?, ?, ?)",
    email,
    prisonCode,
    categoryCode,
  )

  private fun insertDocument(email: String, organisation: String? = null, prison: String? = null): UUID {
    val id = UUID.randomUUID()
    jdbcTemplate.update(
      """
      INSERT INTO court_document
        (id, master_defendant_id, hmcts_court_document_id, prison_document_id, prison_email_address,
         event_type, document_generated_timestamp, ingestion_at, addressed_organisation, addressed_prison, metadata_version)
      VALUES (?, ?, ?, ?, ?, 'PRISON_COURT_REGISTER_GENERATED', ?, ?, ?, ?, 3)
      """.trimIndent(),
      id,
      UUID.randomUUID(),
      UUID.randomUUID(),
      UUID.randomUUID(),
      email,
      LocalDateTime.now().minusDays(1),
      LocalDateTime.now().minusDays(1),
      organisation,
      prison,
    )
    return id
  }

  private fun organisationOf(id: UUID) = column(id, "addressed_organisation", String::class.java)
  private fun prisonOf(id: UUID) = column(id, "addressed_prison", String::class.java)
  private fun mappingOf(id: UUID) = column(id, "delivery_mapping_id", UUID::class.java)
  private fun metadataVersionOf(id: UUID) = column(id, "metadata_version", Int::class.java)
  private fun mappingIdOf(email: String) = jdbcTemplate
    .queryForObject("SELECT id FROM prison_email_mapping WHERE email = ?", UUID::class.java, email)

  private fun <T : Any> column(id: UUID, name: String, type: Class<T>): T? = jdbcTemplate
    .queryForObject("SELECT $name FROM court_document WHERE id = ?", type, id)
}
