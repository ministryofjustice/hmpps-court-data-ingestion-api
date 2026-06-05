package uk.gov.justice.digital.hmpps.courtdataingestionapi.repository

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

data class EmailMapping(
  val prisonCode: String?,
  val sourceType: String?,
)

@Repository
class PrisonEmailMappingRepository(
  private val jdbcTemplate: NamedParameterJdbcTemplate,
) {

  fun findMappingByEmail(normalisedEmail: String): EmailMapping? {
    val sql = """
      SELECT prison_code, source_type
      FROM prison_email_mapping
      WHERE email = :email
    """.trimIndent()

    return jdbcTemplate.query(
      sql,
      mapOf("email" to normalisedEmail),
    ) { rs, _ -> EmailMapping(prisonCode = rs.getString("prison_code"), sourceType = rs.getString("source_type")) }
      .firstOrNull()
  }
}
