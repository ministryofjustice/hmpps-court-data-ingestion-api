package uk.gov.justice.digital.hmpps.courtdataingestionapi.repository

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentDayCount
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.PrisonCourtDocument
import java.sql.ResultSet
import java.time.LocalDate
import java.util.UUID

@Repository
class PrisonCourtDocumentRepository(
  private val jdbcTemplate: NamedParameterJdbcTemplate,
) {

  fun countByDay(roll: Collection<String>, from: LocalDate, toInclusive: LocalDate): List<CourtDocumentDayCount> {
    if (roll.isEmpty()) return emptyList()

    return jdbcTemplate.query(
      """
      SELECT cd.ingestion_at::date              AS day,
             count(*)                           AS documents,
             count(DISTINCT cd.prisoner_number) AS people
        FROM court_document cd
       WHERE cd.prisoner_number = ANY (string_to_array(:roll, ','))
         AND cd.ingestion_at >= :from
         AND cd.ingestion_at <  :toExclusive
       GROUP BY 1
       ORDER BY 1
      """.trimIndent(),
      params(roll, from, toInclusive),
    ) { rs, _ ->
      CourtDocumentDayCount(
        date = rs.getDate("day").toLocalDate(),
        documents = rs.getInt("documents"),
        people = rs.getInt("people"),
      )
    }
  }

  fun countDocuments(roll: Collection<String>, from: LocalDate, toInclusive: LocalDate): Int {
    if (roll.isEmpty()) return 0

    return jdbcTemplate.queryForObject(
      """
      SELECT count(*)
        FROM court_document cd
       WHERE cd.prisoner_number = ANY (string_to_array(:roll, ','))
         AND cd.ingestion_at >= :from
         AND cd.ingestion_at <  :toExclusive
      """.trimIndent(),
      params(roll, from, toInclusive),
      Int::class.java,
    ) ?: 0
  }

  fun findRows(
    roll: Collection<String>,
    from: LocalDate,
    toInclusive: LocalDate,
    limit: Int,
  ): List<PrisonCourtDocument> {
    if (roll.isEmpty()) return emptyList()

    return jdbcTemplate.query(
      """
      SELECT cd.id,
             cd.prison_document_id,
             cd.prisoner_number,
             cd.court_document_type,
             cd.addressed_prison,
             cd.ingestion_at,
             cd.document_generated_timestamp,
             cd.court_hearing_id,
             ch.hearing_date,
             ch.hearing_type,
             ch.court_name,
             refs.case_references
        FROM court_document cd
        LEFT JOIN court_hearing ch ON ch.id = cd.court_hearing_id
        LEFT JOIN (
               SELECT court_document_id, array_agg(DISTINCT case_reference) AS case_references
                 FROM court_document_case
                GROUP BY court_document_id
             ) refs ON refs.court_document_id = cd.id
       WHERE cd.prisoner_number = ANY (string_to_array(:roll, ','))
         AND cd.ingestion_at >= :from
         AND cd.ingestion_at <  :toExclusive
       ORDER BY cd.ingestion_at DESC, cd.id
       LIMIT :limit
      """.trimIndent(),
      params(roll, from, toInclusive).addValue("limit", limit),
      ::mapRow,
    )
  }

  private fun params(roll: Collection<String>, from: LocalDate, toInclusive: LocalDate) = MapSqlParameterSource()
    .addValue("roll", roll.joinToString(","))
    .addValue("from", from.atStartOfDay())
    .addValue("toExclusive", toInclusive.plusDays(1).atStartOfDay())

  private fun mapRow(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int) = PrisonCourtDocument(
    courtDocumentId = rs.getObject("id", UUID::class.java),
    prisonDocumentId = rs.getObject("prison_document_id", UUID::class.java),
    prisonerNumber = rs.getString("prisoner_number"),
    documentType = rs.getString("court_document_type"),
    caseReferences = (rs.getArray("case_references")?.array as? Array<*>)
      ?.filterNotNull()?.map { it.toString() } ?: emptyList(),
    addressedPrison = rs.getString("addressed_prison"),
    courtHearingId = rs.getObject("court_hearing_id", UUID::class.java),
    hearingDate = rs.getDate("hearing_date")?.toLocalDate(),
    hearingType = rs.getString("hearing_type"),
    courtName = rs.getString("court_name"),
    ingestedAt = rs.getTimestamp("ingestion_at").toLocalDateTime(),
    documentGeneratedAt = rs.getTimestamp("document_generated_timestamp")?.toLocalDateTime(),
  )
}
