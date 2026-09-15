package uk.gov.justice.digital.hmpps.courtdataingestionapi.repository

import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

data class DeliveryCategory(
  val code: String,
  val name: String,
  val requiresPrisonCode: Boolean,
  val unmatchedNeedsReview: Boolean,
)

@Repository
class DeliveryCategoryRepository(
  private val jdbcTemplate: NamedParameterJdbcTemplate,
) {

  fun findAll(): List<DeliveryCategory> = jdbcTemplate.query(
    "SELECT * FROM delivery_category ORDER BY name",
    ::map,
  )

  fun findByCode(code: String): DeliveryCategory? = jdbcTemplate
    .query("SELECT * FROM delivery_category WHERE code = :code", mapOf("code" to code), ::map)
    .firstOrNull()

  /** Returns null when the code is already taken, so the caller can answer 409. */
  fun create(category: DeliveryCategory, createdBy: String?): DeliveryCategory? = try {
    jdbcTemplate.update(
      """
      INSERT INTO delivery_category
        (code, name, requires_prison_code, unmatched_needs_review, created_by)
      VALUES (:code, :name, :requiresPrisonCode, :unmatchedNeedsReview, :createdBy)
      """.trimIndent(),
      mapOf(
        "code" to category.code,
        "name" to category.name,
        "requiresPrisonCode" to category.requiresPrisonCode,
        "unmatchedNeedsReview" to category.unmatchedNeedsReview,
        "createdBy" to createdBy,
      ),
    )
    category
  } catch (_: DuplicateKeyException) {
    null
  }

  private fun map(rs: java.sql.ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int) = DeliveryCategory(
    code = rs.getString("code"),
    name = rs.getString("name"),
    requiresPrisonCode = rs.getBoolean("requires_prison_code"),
    unmatchedNeedsReview = rs.getBoolean("unmatched_needs_review"),
  )
}
