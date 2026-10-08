package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime
import java.util.UUID

private const val SUPPORT_ROLE = "COURTCASE_RELEASEDATE_SUPPORT"
private const val UNCLASSIFIED = "nobody.knows@justice.gov.uk"
private const val MAPPED = "omu.leeds@justice.gov.uk"

class DeliveryAddressAdminControllerTest : IntegrationTestBase() {

  @Autowired
  private lateinit var jdbcTemplate: JdbcTemplate

  @BeforeEach
  fun setUp() {
    courtDocumentRepository.deleteAll()
    jdbcTemplate.update("DELETE FROM prison_email_mapping")
    jdbcTemplate.update("DELETE FROM delivery_category WHERE code NOT IN ('PRISON', 'PECS')")
  }

  @Test
  fun `delivery addresses are rejected without a token`() {
    webTestClient.get().uri("/admin/delivery-addresses?classified=false").exchange().expectStatus().isUnauthorized
  }

  @Test
  fun `delivery addresses are rejected without the support role`() {
    webTestClient.get().uri("/admin/delivery-addresses?classified=false")
      .headers(setAuthorisation(roles = listOf("SOME_OTHER_ROLE")))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `unclassified addresses are grouped by address, with mapped addresses excluded`() {
    insertDocument(UNCLASSIFIED, prisonerNumber = "A1111AA")
    insertDocument(UNCLASSIFIED, prisonerNumber = null)
    insertMapping("pecs.south@geoamey.co.uk", prisonCode = null, categoryCode = "PECS")
    insertDocument("pecs.south@geoamey.co.uk", prisonerNumber = "A2222AA", deliverySource = "PECS")
    insertMapping(MAPPED)
    insertDocument(MAPPED, prisonerNumber = "A3333AA")
    insertDocument(MAPPED, prisonerNumber = "A4444AA", mappingId = mappingIdOf(MAPPED))

    webTestClient.get().uri("/admin/delivery-addresses?classified=false")
      .headers(setAuthorisation(roles = listOf(SUPPORT_ROLE)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].emailAddress").isEqualTo(UNCLASSIFIED)
      .jsonPath("$[0].documentCount").isEqualTo(2)
      .jsonPath("$[0].matchedToPersonCount").isEqualTo(1)
      .jsonPath("$[0].recentDocumentTypes[0]").isEqualTo("PRISON_COURT_REGISTER")
  }

  @Test
  fun `a category is created and the code is upper cased`() {
    webTestClient.post().uri("/admin/delivery-categories")
      .headers(setAuthorisation(roles = listOf(SUPPORT_ROLE)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(mapOf("code" to "youth_custody", "name" to "Youth custody", "unmatchedNeedsReview" to false))
      .exchange()
      .expectStatus().isCreated

    assertThat(categoryCodes()).contains("YOUTH_CUSTODY")
  }

  @Test
  fun `a duplicate category is a conflict`() {
    webTestClient.post().uri("/admin/delivery-categories")
      .headers(setAuthorisation(roles = listOf(SUPPORT_ROLE)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(mapOf("code" to "PRISON", "name" to "Prison"))
      .exchange()
      .expectStatus().isEqualTo(409)
  }

  @Test
  fun `a category code with punctuation is rejected`() {
    webTestClient.post().uri("/admin/delivery-categories")
      .headers(setAuthorisation(roles = listOf(SUPPORT_ROLE)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(mapOf("code" to "youth custody<script>", "name" to "Youth custody"))
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `a category name with markup is rejected`() {
    webTestClient.post().uri("/admin/delivery-categories")
      .headers(setAuthorisation(roles = listOf(SUPPORT_ROLE)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(mapOf("code" to "YOUTH_CUSTODY", "name" to "<img src=x onerror=alert(1)>"))
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `classifying against an unknown category is a not found`() {
    webTestClient.post().uri("/admin/delivery-addresses")
      .headers(setAuthorisation(roles = listOf(SUPPORT_ROLE)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(mapOf("emailAddress" to UNCLASSIFIED, "categoryCode" to "NO_SUCH_CATEGORY"))
      .exchange()
      .expectStatus().isNotFound
  }

  @Test
  fun `classifying as a prison without a prison code is unprocessable`() {
    webTestClient.post().uri("/admin/delivery-addresses")
      .headers(setAuthorisation(roles = listOf(SUPPORT_ROLE)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(mapOf("emailAddress" to UNCLASSIFIED, "categoryCode" to "PRISON"))
      .exchange()
      .expectStatus().isEqualTo(422)
  }

  @Test
  fun `an invalid email address is rejected before it reaches the service`() {
    webTestClient.post().uri("/admin/delivery-addresses")
      .headers(setAuthorisation(roles = listOf(SUPPORT_ROLE)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(mapOf("emailAddress" to "not-an-address", "categoryCode" to "PRISON", "prisonCode" to "LEI"))
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `classifying an address saves the mapping and leaves existing documents for the backfill`() {
    insertDocument(UNCLASSIFIED, prisonerNumber = "A1111AA")
    insertDocument(UNCLASSIFIED, prisonerNumber = "A2222AA")
    val runsBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM backfill_run", Int::class.java)

    webTestClient.post().uri("/admin/delivery-addresses")
      .headers(setAuthorisation(roles = listOf(SUPPORT_ROLE)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(mapOf("emailAddress" to UNCLASSIFIED, "categoryCode" to "PRISON", "prisonCode" to "LEI"))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.backfillOutcome").isEqualTo("pending")

    assertThat(mappedPrisonFor(UNCLASSIFIED)).isEqualTo("LEI")
    assertThat(
      jdbcTemplate.queryForObject(
        "SELECT count(*) FROM court_document WHERE prison_email_address = ? AND delivery_mapping_id IS NOT NULL",
        Int::class.java,
        UNCLASSIFIED,
      ),
    ).isZero()
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM backfill_run", Int::class.java)).isEqualTo(runsBefore)
  }

  @Test
  fun `previewing a classification reports the category without counting documents`() {
    insertDocument(UNCLASSIFIED, prisonerNumber = "A1111AA")

    webTestClient.post().uri("/admin/delivery-addresses/preview")
      .headers(setAuthorisation(roles = listOf(SUPPORT_ROLE)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(mapOf("emailAddress" to UNCLASSIFIED, "categoryCode" to "PRISON", "prisonCode" to "LEI"))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.emailAddress").isEqualTo(UNCLASSIFIED)
      .jsonPath("$.category.code").isEqualTo("PRISON")
      .jsonPath("$.prisonCode").isEqualTo("LEI")
      .jsonPath("$.replacesExisting").isEqualTo(false)
      .jsonPath("$.documentsAffected").doesNotExist()
      .jsonPath("$.peopleAffected").doesNotExist()
      .jsonPath("$.currentLocations").doesNotExist()
  }

  @Test
  fun `previewing a classification flags an address that already has a mapping`() {
    insertMapping(MAPPED)

    webTestClient.post().uri("/admin/delivery-addresses/preview")
      .headers(setAuthorisation(roles = listOf(SUPPORT_ROLE)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(mapOf("emailAddress" to MAPPED, "categoryCode" to "PRISON", "prisonCode" to "LEI"))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.replacesExisting").isEqualTo(true)
  }

  @Test
  fun `previewing an unknown category is not found`() {
    webTestClient.post().uri("/admin/delivery-addresses/preview")
      .headers(setAuthorisation(roles = listOf(SUPPORT_ROLE)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(mapOf("emailAddress" to UNCLASSIFIED, "categoryCode" to "NO_SUCH_CATEGORY"))
      .exchange()
      .expectStatus().isNotFound
  }

  private fun categoryCodes() = jdbcTemplate.queryForList("SELECT code FROM delivery_category", String::class.java)

  private fun mappedPrisonFor(email: String) = jdbcTemplate
    .queryForObject("SELECT prison_code FROM prison_email_mapping WHERE email = ?", String::class.java, email)

  private fun insertMapping(email: String, prisonCode: String? = "LEI", categoryCode: String = "PRISON") = jdbcTemplate.update(
    "INSERT INTO prison_email_mapping (email, prison_code, category_code) VALUES (?, ?, ?)",
    email,
    prisonCode,
    categoryCode,
  )

  private fun mappingIdOf(email: String) = jdbcTemplate
    .queryForObject("SELECT id FROM prison_email_mapping WHERE email = ?", UUID::class.java, email)

  private fun insertDocument(email: String, prisonerNumber: String?, deliverySource: String? = null, mappingId: UUID? = null) = jdbcTemplate.update(
    """
      INSERT INTO court_document
        (id, master_defendant_id, hmcts_court_document_id, prison_document_id, prison_email_address,
         event_type, court_document_type, document_generated_timestamp, ingestion_at, prisoner_number, delivery_source,
         delivery_mapping_id)
      VALUES (?, ?, ?, ?, ?, 'PRISON_COURT_REGISTER_GENERATED', 'PRISON_COURT_REGISTER', ?, ?, ?, ?, ?)
    """.trimIndent(),
    UUID.randomUUID(),
    UUID.randomUUID(),
    UUID.randomUUID(),
    UUID.randomUUID(),
    email,
    LocalDateTime.now().minusDays(1),
    LocalDateTime.now().minusDays(1),
    prisonerNumber,
    deliverySource,
    mappingId,
  )

  @Test
  fun `an escort mailbox with no mapping is listed, so it can be classified`() {
    insertDocument("pecs.north@geoamey.co.uk", prisonerNumber = "A2222AA", deliverySource = "PECS")

    webTestClient.get().uri("/admin/delivery-addresses?classified=false")
      .headers(setAuthorisation(roles = listOf(SUPPORT_ROLE)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].emailAddress").isEqualTo("pecs.north@geoamey.co.uk")
  }

  @Test
  fun `classified addresses count the documents attached to their mapping`() {
    insertMapping(MAPPED)
    insertDocument(MAPPED, prisonerNumber = "A3333AA", mappingId = mappingIdOf(MAPPED))
    insertDocument(MAPPED, prisonerNumber = "A5555AA")

    webTestClient.get().uri("/admin/delivery-addresses?classified=true")
      .headers(setAuthorisation(roles = listOf(SUPPORT_ROLE)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$[0].documentCount").isEqualTo(1)
  }

  @Test
  fun `classified addresses are listed with their category and prison`() {
    insertMapping(MAPPED)
    insertDocument(MAPPED, prisonerNumber = "A3333AA", mappingId = mappingIdOf(MAPPED))

    webTestClient.get().uri("/admin/delivery-addresses?classified=true")
      .headers(setAuthorisation(roles = listOf(SUPPORT_ROLE)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].emailAddress").isEqualTo(MAPPED)
      .jsonPath("$[0].categoryCode").isEqualTo("PRISON")
      .jsonPath("$[0].prisonCode").isEqualTo("LEI")
      .jsonPath("$[0].documentCount").isEqualTo(1)
  }

  @Test
  fun `classified addresses can be narrowed to one category`() {
    insertMapping(MAPPED)

    webTestClient.get().uri("/admin/delivery-addresses?classified=true&category=YOUTH_CUSTODY")
      .headers(setAuthorisation(roles = listOf(SUPPORT_ROLE)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(0)
  }

  @Test
  fun `an address classified before any traffic arrives still appears`() {
    insertMapping(MAPPED)

    webTestClient.get().uri("/admin/delivery-addresses?classified=true")
      .headers(setAuthorisation(roles = listOf(SUPPORT_ROLE)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$[0].documentCount").isEqualTo(0)
  }
}
