package uk.gov.justice.digital.hmpps.courtdataingestionapi.repository

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

data class UnclassifiedAddress(
  val emailAddress: String,
  val documentCount: Int,
  val matchedToPersonCount: Int,
  val firstSeen: LocalDateTime,
  val lastSeen: LocalDateTime,
  val recentDocumentTypes: List<String>,
)

/**
 * Reads for the unclassified delivery address admin.
 *
 * Grouped by address rather than listed by document on purpose. The useful artefact is a short
 * list of addresses to classify, and an address row is a count with no person on it, which keeps
 * identity off a screen that has no caseload gate.
 */
@Repository
class UnclassifiedAddressRepository(
  private val jdbcTemplate: NamedParameterJdbcTemplate,
) {

  fun findUnclassified(): List<UnclassifiedAddress> = jdbcTemplate.query(
    """
    SELECT cd.prison_email_address                                         AS email_address,
           count(*)                                                        AS document_count,
           count(*) FILTER (WHERE cd.prisoner_number IS NOT NULL)          AS matched_count,
           min(cd.ingestion_at)                                            AS first_seen,
           max(cd.ingestion_at)                                            AS last_seen,
           (array_agg(DISTINCT cd.court_document_type::text))[1:5]         AS document_types
      FROM court_document cd
     WHERE cd.addressed_prison IS NULL
       AND cd.prison_email_address IS NOT NULL
       AND cd.delivery_source IS DISTINCT FROM 'PECS'
       AND NOT EXISTS (
             SELECT 1 FROM prison_email_mapping m WHERE m.email = cd.prison_email_address
           )
     GROUP BY cd.prison_email_address
     ORDER BY document_count DESC
    """.trimIndent(),
  ) { rs, _ ->
    UnclassifiedAddress(
      emailAddress = rs.getString("email_address"),
      documentCount = rs.getInt("document_count"),
      matchedToPersonCount = rs.getInt("matched_count"),
      firstSeen = rs.getTimestamp("first_seen").toLocalDateTime(),
      lastSeen = rs.getTimestamp("last_seen").toLocalDateTime(),
      recentDocumentTypes = (rs.getArray("document_types")?.array as? Array<*>)
        ?.filterNotNull()?.map { it.toString() } ?: emptyList(),
    )
  }

  fun countDocumentsFor(normalisedEmail: String): Int = jdbcTemplate.queryForObject(
    "SELECT count(*) FROM court_document WHERE prison_email_address = :email AND addressed_prison IS NULL",
    mapOf("email" to normalisedEmail),
    Int::class.java,
  ) ?: 0

  /**
   * Distinct people behind the documents a classification would move. Capped, because the
   * caller looks each one up in prisoner-search to build the location distribution.
   */
  fun prisonerNumbersFor(normalisedEmail: String, limit: Int): List<String> = jdbcTemplate.query(
    """
    SELECT DISTINCT prisoner_number
      FROM court_document
     WHERE prison_email_address = :email
       AND addressed_prison IS NULL
       AND prisoner_number IS NOT NULL
     LIMIT :limit
    """.trimIndent(),
    MapSqlParameterSource().addValue("email", normalisedEmail).addValue("limit", limit),
  ) { rs, _ -> rs.getString("prisoner_number") }

  fun countDistinctPeopleFor(normalisedEmail: String): Int = jdbcTemplate.queryForObject(
    """
    SELECT count(DISTINCT prisoner_number)
      FROM court_document
     WHERE prison_email_address = :email AND addressed_prison IS NULL AND prisoner_number IS NOT NULL
    """.trimIndent(),
    mapOf("email" to normalisedEmail),
    Int::class.java,
  ) ?: 0
}
