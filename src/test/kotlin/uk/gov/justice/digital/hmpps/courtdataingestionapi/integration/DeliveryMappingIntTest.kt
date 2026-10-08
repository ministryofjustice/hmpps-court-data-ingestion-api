package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime
import java.util.UUID

class DeliveryMappingIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var jdbcTemplate: JdbcTemplate

  @BeforeEach
  fun setUp() {
    courtDocumentRepository.deleteAll()
    jdbcTemplate.update("DELETE FROM prison_email_mapping")
    jdbcTemplate.update(
      """
      INSERT INTO delivery_category (code, name, requires_prison_code, unmatched_needs_review)
      VALUES ('YOUTH_CUSTODY', 'Youth custody', FALSE, TRUE)
      ON CONFLICT (code) DO NOTHING
      """.trimIndent(),
    )
  }

  @Test
  fun `attaches the mapping to a document classified before mappings were recorded, leaving its columns alone`() {
    insertMapping("omu.leeds@justice.gov.uk", prisonCode = "LEI", categoryCode = "PRISON")
    val id = insertDocument("omu.leeds@justice.gov.uk", deliverySource = "PRISON", addressedPrison = "LEI")

    applyMigration()

    assertThat(mappingOf(id)).isEqualTo(mappingIdOf("omu.leeds@justice.gov.uk"))
    assertThat(column(id, "addressed_prison", String::class.java)).isEqualTo("LEI")
    assertThat(column(id, "delivery_source", String::class.java)).isEqualTo("PRISON")
  }

  @Test
  fun `matches ignoring case and surrounding spaces on either side`() {
    insertMapping("omu.leeds@justice.gov.uk", prisonCode = "LEI", categoryCode = "PRISON")
    insertMapping("  Mixed.Case@Justice.GOV.uk ", prisonCode = "MDI", categoryCode = "PRISON")
    val messyDocument = insertDocument("  OMU.Leeds@Justice.gov.UK  ")
    val messyMapping = insertDocument("mixed.case@justice.gov.uk")

    applyMigration()

    assertThat(mappingOf(messyDocument)).isEqualTo(mappingIdOf("omu.leeds@justice.gov.uk"))
    assertThat(mappingOf(messyMapping)).isEqualTo(mappingIdOf("  Mixed.Case@Justice.GOV.uk "))
  }

  @Test
  fun `does not choose between mappings that normalise to the same address`() {
    insertMapping("dup@justice.gov.uk", prisonCode = "LEI", categoryCode = "PRISON")
    insertMapping("DUP@justice.gov.uk ", prisonCode = "MDI", categoryCode = "PRISON")
    val id = insertDocument("dup@justice.gov.uk")

    applyMigration()

    assertThat(mappingOf(id)).isNull()
  }

  @Test
  fun `keeps a mapping that is already attached`() {
    insertMapping("ycs.warrants@justice.gov.uk", prisonCode = null, categoryCode = "YOUTH_CUSTODY")
    insertMapping("ycs.other@justice.gov.uk", prisonCode = null, categoryCode = "YOUTH_CUSTODY")
    val id = insertDocument("ycs.warrants@justice.gov.uk", mappingId = mappingIdOf("ycs.other@justice.gov.uk"))

    applyMigration()

    assertThat(mappingOf(id)).isEqualTo(mappingIdOf("ycs.other@justice.gov.uk"))
  }

  @Test
  fun `leaves a document with no mapping for its address unattached`() {
    val id = insertDocument("nobody.knows@justice.gov.uk")

    applyMigration()

    assertThat(mappingOf(id)).isNull()
  }

  @Test
  fun `leaves an escort mailbox with no mapping unattached, to be classified`() {
    val id = insertDocument("SheffieldCC@geoamey.co.uk", deliverySource = "PECS")

    applyMigration()

    assertThat(mappingOf(id)).isNull()
    assertThat(mappingCount()).isZero()
  }

  @Test
  fun `running it again changes nothing`() {
    insertMapping("omu.leeds@justice.gov.uk", prisonCode = "LEI", categoryCode = "PRISON")
    val id = insertDocument("omu.leeds@justice.gov.uk")
    insertDocument("SheffieldCC@geoamey.co.uk", deliverySource = "PECS")
    applyMigration()
    val mappings = mappingCount()

    applyMigration()

    assertThat(mappingCount()).isEqualTo(mappings)
    assertThat(mappingOf(id)).isEqualTo(mappingIdOf("omu.leeds@justice.gov.uk"))
  }

  @Test
  fun `a loaded document carries its mapping, giving the prison only where the category uses one`() {
    insertMapping("omu.leeds@justice.gov.uk", prisonCode = "LEI", categoryCode = "PRISON")
    insertMapping("ycs.warrants@justice.gov.uk", prisonCode = "WYI", categoryCode = "YOUTH_CUSTODY")
    val prison = insertDocument("omu.leeds@justice.gov.uk", mappingId = mappingIdOf("omu.leeds@justice.gov.uk"))
    val youth = insertDocument("ycs.warrants@justice.gov.uk", mappingId = mappingIdOf("ycs.warrants@justice.gov.uk"))
    val unmapped = insertDocument("nobody.knows@justice.gov.uk")

    val prisonMapping = courtDocumentRepository.findById(prison).get().deliveryMapping
    val youthMapping = courtDocumentRepository.findById(youth).get().deliveryMapping

    assertThat(prisonMapping?.category?.code).isEqualTo("PRISON")
    assertThat(prisonMapping?.destinationPrison).isEqualTo("LEI")
    assertThat(youthMapping?.category?.code).isEqualTo("YOUTH_CUSTODY")
    assertThat(youthMapping?.destinationPrison).isNull()
    assertThat(courtDocumentRepository.findById(unmapped).get().deliveryMapping).isNull()
  }

  @Test
  fun `saving a document does not write its mapping`() {
    insertMapping("omu.leeds@justice.gov.uk", prisonCode = "LEI", categoryCode = "PRISON")
    val id = insertDocument("omu.leeds@justice.gov.uk", mappingId = mappingIdOf("omu.leeds@justice.gov.uk"))
    val document = courtDocumentRepository.findById(id).get()

    courtDocumentRepository.save(document.apply { metadataVersion = 7 })

    assertThat(mappingOf(id)).isEqualTo(mappingIdOf("omu.leeds@justice.gov.uk"))
    assertThat(jdbcTemplate.queryForObject("SELECT prison_code FROM prison_email_mapping WHERE email = ?", String::class.java, "omu.leeds@justice.gov.uk")).isEqualTo("LEI")
  }

  private fun applyMigration() {
    jdbcTemplate.execute(
      ClassPathResource("migration/postgres/V42__delivery_mapping_source_of_truth.sql").getContentAsString(Charsets.UTF_8),
    )
  }

  private fun insertMapping(email: String, prisonCode: String?, categoryCode: String) = jdbcTemplate.update(
    "INSERT INTO prison_email_mapping (email, prison_code, category_code) VALUES (?, ?, ?)",
    email,
    prisonCode,
    categoryCode,
  )

  private fun insertDocument(
    email: String,
    deliverySource: String? = null,
    addressedPrison: String? = null,
    mappingId: UUID? = null,
  ): UUID {
    val id = UUID.randomUUID()
    jdbcTemplate.update(
      """
      INSERT INTO court_document
        (id, master_defendant_id, hmcts_court_document_id, prison_document_id, prison_email_address,
         event_type, document_generated_timestamp, ingestion_at, delivery_source, addressed_prison, delivery_mapping_id)
      VALUES (?, ?, ?, ?, ?, 'PRISON_COURT_REGISTER_GENERATED', ?, ?, ?, ?, ?)
      """.trimIndent(),
      id,
      UUID.randomUUID(),
      UUID.randomUUID(),
      UUID.randomUUID(),
      email,
      LocalDateTime.now().minusDays(1),
      LocalDateTime.now().minusDays(1),
      deliverySource,
      addressedPrison,
      mappingId,
    )
    return id
  }

  private fun mappingCount() = jdbcTemplate.queryForObject("SELECT count(*) FROM prison_email_mapping", Int::class.java)

  private fun mappingIdOf(email: String): UUID? = jdbcTemplate
    .queryForObject("SELECT id FROM prison_email_mapping WHERE email = ?", UUID::class.java, email)

  private fun mappingOf(id: UUID): UUID? = column(id, "delivery_mapping_id", UUID::class.java)

  private fun <T : Any> column(id: UUID, name: String, type: Class<T>): T? = jdbcTemplate
    .queryForObject("SELECT $name FROM court_document WHERE id = ?", type, id)
}
