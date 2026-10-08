package uk.gov.justice.digital.hmpps.courtdataingestionapi.repository

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

data class EmailMapping(
  val id: UUID,
  val email: String,
  val prisonCode: String?,
  val sourceType: String?,
  val categoryCode: String?,
)

@Repository
class PrisonEmailMappingRepository(
  private val jdbcTemplate: NamedParameterJdbcTemplate,
) {

  fun findMappingByEmail(normalisedEmail: String): EmailMapping? {
    val sql = """
      SELECT id, email, prison_code, source_type, category_code
      FROM prison_email_mapping
      WHERE email = :email
    """.trimIndent()

    return jdbcTemplate.query(sql, mapOf("email" to normalisedEmail), ::map).firstOrNull()
  }

  fun upsert(
    normalisedEmail: String,
    categoryCode: String,
    prisonCode: String?,
    createdBy: String?,
  ): EmailMapping {
    jdbcTemplate.update(
      """
      INSERT INTO prison_email_mapping (email, prison_code, category_code, created_by)
      VALUES (:email, :prisonCode, :categoryCode, :createdBy)
      ON CONFLICT (email) DO UPDATE
        SET prison_code = EXCLUDED.prison_code,
            category_code = EXCLUDED.category_code,
            created_by = EXCLUDED.created_by
      """.trimIndent(),
      mapOf(
        "email" to normalisedEmail,
        "prisonCode" to prisonCode,
        "categoryCode" to categoryCode,
        "createdBy" to createdBy,
      ),
    )
    return findMappingByEmail(normalisedEmail)!!
  }

  private fun map(rs: java.sql.ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int) = EmailMapping(
    id = rs.getObject("id", UUID::class.java),
    email = rs.getString("email"),
    prisonCode = rs.getString("prison_code"),
    sourceType = rs.getString("source_type"),
    categoryCode = rs.getString("category_code"),
  )
}
