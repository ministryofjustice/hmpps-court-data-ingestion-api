package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import java.time.LocalDateTime
import java.util.UUID

private const val MAPPED_EMAIL = "omu.leeds@justice.gov.uk"
private const val YOUTH_EMAIL = "ycs.warrants@justice.gov.uk"
private const val UNMAPPED_EMAIL = "nobody.knows@justice.gov.uk"

class AddressedPrisonReresolveBackfillIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var jdbcTemplate: JdbcTemplate

  @Autowired
  override lateinit var courtDocumentRepository: CourtDocumentRepository

  @BeforeEach
  fun setUp() {
    courtDocumentRepository.deleteAll()
    jdbcTemplate.update("DELETE FROM prison_email_mapping")

    insertDeliveryCategory("YOUTH_CUSTODY", "Youth custody", requiresPrisonCode = false)
    insertMapping(MAPPED_EMAIL, prisonCode = "LEI", categoryCode = "PRISON")
    insertMapping(YOUTH_EMAIL, prisonCode = null, categoryCode = "YOUTH_CUSTODY")
  }

  @Test
  fun `a document from a newly mapped address gets its prison and its mapping`() {
    val id = insertDocument(MAPPED_EMAIL)

    runBackfill("addressed-prison-reresolve")

    assertThat(addressedPrisonOf(id)).isEqualTo("LEI")
    assertThat(deliveryMappingOf(id)).isEqualTo(mappingIdOf(MAPPED_EMAIL))
  }

  @Test
  fun `a document from an address classified as youth custody keeps a null prison but records the mapping`() {
    val id = insertDocument(YOUTH_EMAIL)

    runBackfill("addressed-prison-reresolve")

    assertThat(addressedPrisonOf(id)).isNull()
    assertThat(deliveryMappingOf(id)).isEqualTo(mappingIdOf(YOUTH_EMAIL))
  }

  @Test
  fun `a document from an address with no mapping is left alone`() {
    val id = insertDocument(UNMAPPED_EMAIL)

    runBackfill("addressed-prison-reresolve")

    assertThat(addressedPrisonOf(id)).isNull()
    assertThat(deliveryMappingOf(id)).isNull()
  }

  @Test
  fun `the update does not disturb the rest of the row`() {
    val id = insertDocument(MAPPED_EMAIL)
    val before = jdbcTemplate.queryForMap("SELECT * FROM court_document WHERE id = ?", id)

    runBackfill("addressed-prison-reresolve")

    val after = jdbcTemplate.queryForMap("SELECT * FROM court_document WHERE id = ?", id)
    val changed = after.filterNot { (key, value) -> before[key] == value }.keys
    assertThat(changed).containsExactlyInAnyOrder("addressed_prison", "delivery_mapping_id", "delivery_source")
  }

  @Test
  fun `an already classified document is not selected again`() {
    val id = insertDocument(MAPPED_EMAIL)
    runBackfill("addressed-prison-reresolve")

    jdbcTemplate.update("UPDATE prison_email_mapping SET prison_code = 'MDI' WHERE email = ?", MAPPED_EMAIL)
    runBackfill("addressed-prison-reresolve")

    assertThat(addressedPrisonOf(id)).isEqualTo("LEI")
  }

  private fun insertDeliveryCategory(code: String, name: String, requiresPrisonCode: Boolean) = jdbcTemplate.update(
    """
      INSERT INTO delivery_category (code, name, requires_prison_code, unmatched_needs_review)
      VALUES (?, ?, ?, FALSE)
      ON CONFLICT (code) DO NOTHING
    """.trimIndent(),
    code,
    name,
    requiresPrisonCode,
  )

  private fun insertMapping(email: String, prisonCode: String?, categoryCode: String) {
    jdbcTemplate.update(
      "INSERT INTO prison_email_mapping (email, prison_code, category_code) VALUES (?, ?, ?)",
      email,
      prisonCode,
      categoryCode,
    )
  }

  private fun insertDocument(email: String): UUID {
    val id = UUID.randomUUID()
    jdbcTemplate.update(
      """
      INSERT INTO court_document
        (id, master_defendant_id, hmcts_court_document_id, prison_document_id, prison_email_address,
         event_type, document_generated_timestamp, ingestion_at)
      VALUES (?, ?, ?, ?, ?, 'PRISON_COURT_REGISTER_GENERATED', ?, ?)
      """.trimIndent(),
      id,
      UUID.randomUUID(),
      UUID.randomUUID(),
      UUID.randomUUID(),
      email,
      LocalDateTime.now().minusDays(1),
      LocalDateTime.now().minusDays(1),
    )
    return id
  }

  private fun addressedPrisonOf(id: UUID) = jdbcTemplate
    .queryForObject("SELECT addressed_prison FROM court_document WHERE id = ?", String::class.java, id)

  private fun deliveryMappingOf(id: UUID) = jdbcTemplate
    .queryForObject("SELECT delivery_mapping_id FROM court_document WHERE id = ?", UUID::class.java, id)

  private fun mappingIdOf(email: String) = jdbcTemplate
    .queryForObject("SELECT id FROM prison_email_mapping WHERE email = ?", UUID::class.java, email)
}
