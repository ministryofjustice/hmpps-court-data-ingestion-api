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
  val categoryCode: String? = null,
  val prisonCode: String? = null,
)

@Repository
class UnclassifiedAddressRepository(
  private val jdbcTemplate: NamedParameterJdbcTemplate,
) {

  fun findUnclassified(): List<UnclassifiedAddress> = jdbcTemplate.query(
    """
    SELECT lower(trim(cd.prison_email_address))                            AS email_address,
           NULL                                                            AS category_code,
           NULL                                                            AS prison_code,
           count(*)                                                        AS document_count,
           count(*) FILTER (WHERE cd.prisoner_number IS NOT NULL)          AS matched_count,
           min(cd.ingestion_at)                                            AS first_seen,
           max(cd.ingestion_at)                                            AS last_seen,
           string_agg(DISTINCT cd.court_document_type, ',')                AS document_types
      FROM court_document cd
     WHERE cd.delivery_mapping_id IS NULL
       AND cd.prison_email_address IS NOT NULL
       AND NOT EXISTS (
             SELECT 1 FROM prison_email_mapping m WHERE m.email = lower(trim(cd.prison_email_address))
           )
     GROUP BY lower(trim(cd.prison_email_address))
     ORDER BY document_count DESC
    """.trimIndent(),
    ::map,
  )

  fun findClassified(categoryCode: String?): List<UnclassifiedAddress> = jdbcTemplate.query(
    """
    SELECT m.email                                                         AS email_address,
           m.category_code                                                 AS category_code,
           m.prison_code                                                   AS prison_code,
           count(cd.id)                                                    AS document_count,
           count(*) FILTER (WHERE cd.prisoner_number IS NOT NULL)          AS matched_count,
           min(cd.ingestion_at)                                            AS first_seen,
           max(cd.ingestion_at)                                            AS last_seen,
           string_agg(DISTINCT cd.court_document_type, ',')                AS document_types
      FROM prison_email_mapping m
      LEFT JOIN court_document cd
             ON cd.delivery_mapping_id = m.id
     WHERE (CAST(:categoryCode AS TEXT) IS NULL OR m.category_code = CAST(:categoryCode AS TEXT))
     GROUP BY m.email, m.category_code, m.prison_code
     ORDER BY document_count DESC
    """.trimIndent(),
    MapSqlParameterSource().addValue("categoryCode", categoryCode),
    ::map,
  )

  private fun map(rs: java.sql.ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int) = UnclassifiedAddress(
    emailAddress = rs.getString("email_address"),
    categoryCode = rs.getString("category_code"),
    prisonCode = rs.getString("prison_code"),
    documentCount = rs.getInt("document_count"),
    matchedToPersonCount = rs.getInt("matched_count"),
    firstSeen = rs.getTimestamp("first_seen")?.toLocalDateTime() ?: LocalDateTime.MIN,
    lastSeen = rs.getTimestamp("last_seen")?.toLocalDateTime() ?: LocalDateTime.MIN,
    recentDocumentTypes = rs.getString("document_types")
      ?.split(",")
      ?.filter { it.isNotBlank() }
      ?.take(5)
      ?: emptyList(),
  )
}
