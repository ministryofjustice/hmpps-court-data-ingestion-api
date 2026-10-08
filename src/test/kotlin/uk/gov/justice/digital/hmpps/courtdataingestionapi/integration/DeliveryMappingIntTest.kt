package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.MappedDestination
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.PrisonEmailMappingRepository
import java.time.LocalDateTime
import java.util.UUID

class DeliveryMappingIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var jdbcTemplate: JdbcTemplate

  @Autowired
  private lateinit var prisonEmailMappingRepository: PrisonEmailMappingRepository

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
  fun `gives an escort mailbox recognised only by the fallback a pecs mapping, and attaches it`() {
    val id = insertDocument("SheffieldCC@geoamey.co.uk", deliverySource = "PECS")

    applyMigration()

    val mapping = jdbcTemplate.queryForMap("SELECT id, category_code, prison_code FROM prison_email_mapping WHERE email = ?", "sheffieldcc@geoamey.co.uk")
    assertThat(mapping["category_code"]).isEqualTo("PECS")
    assertThat(mapping["prison_code"]).isNull()
    assertThat(mappingOf(id)).isEqualTo(mapping["id"])
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
  fun `a mapping's destination is its category, and its prison only where the category uses one`() {
    insertMapping("omu.leeds@justice.gov.uk", prisonCode = "LEI", categoryCode = "PRISON")
    insertMapping("ycs.warrants@justice.gov.uk", prisonCode = "WYI", categoryCode = "YOUTH_CUSTODY")
    val prison = mappingIdOf("omu.leeds@justice.gov.uk")!!
    val youth = mappingIdOf("ycs.warrants@justice.gov.uk")!!

    val destinations = prisonEmailMappingRepository.findDestinations(setOf(prison, youth, UUID.randomUUID()))

    assertThat(destinations).containsExactlyInAnyOrderEntriesOf(
      mapOf(
        prison to MappedDestination(categoryCode = "PRISON", prisonCode = "LEI"),
        youth to MappedDestination(categoryCode = "YOUTH_CUSTODY", prisonCode = null),
      ),
    )
  }

  @Test
  fun `looking up no mappings asks the database nothing`() {
    assertThat(prisonEmailMappingRepository.findDestinations(emptySet())).isEmpty()
  }

  private fun applyMigration() {
    jdbcTemplate.execute(
      ClassPathResource("migration/postgres/V41__delivery_mapping_source_of_truth.sql").getContentAsString(Charsets.UTF_8),
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
