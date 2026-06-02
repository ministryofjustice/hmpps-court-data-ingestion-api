package uk.gov.justice.digital.hmpps.courtdataingestionapi.repository

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class PrisonEmailMappingRepository(
  private val jdbcTemplate: NamedParameterJdbcTemplate,
) {

  fun findPrisonCodeByEmail(normalisedEmail: String): String? {
    val sql = """
      SELECT prison_code
      FROM prison_email_mapping
      WHERE email = :email
    """.trimIndent()

    return jdbcTemplate.query(
      sql,
      mapOf("email" to normalisedEmail),
    ) { rs, _ -> rs.getString("prison_code") }
      .firstOrNull()
  }
}
