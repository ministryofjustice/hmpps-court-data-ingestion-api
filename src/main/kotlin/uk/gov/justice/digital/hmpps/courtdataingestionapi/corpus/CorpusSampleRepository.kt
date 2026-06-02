package uk.gov.justice.digital.hmpps.courtdataingestionapi.corpus

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Identity of a sampled court_document row, before any document download. */
data class CorpusRow(
  val courtDocumentId: UUID,
  val prisonDocumentId: UUID,
  val eventType: String,
  val courtDocumentType: String?,
  val documentGeneratedTimestamp: String?,
  val ingestionAt: String?,
)

@Repository
class CorpusSampleRepository(
  @PersistenceContext private val em: EntityManager,
) {

  @Transactional(readOnly = true)
  fun sample(
    eventType: String?,
    courtDocumentType: String?,
    limit: Int,
    seed: Double?,
  ): List<CorpusRow> {
    if (seed != null) {
      em.createNativeQuery("SELECT setseed(:seed)").setParameter("seed", seed).singleResult
    }

    val where = buildList {
      if (eventType != null) add("event_type = :eventType")
      if (courtDocumentType != null) add("court_document_type = :courtDocumentType")
    }.joinToString(" AND ").let { if (it.isEmpty()) "" else "WHERE $it" }

    val query = em.createNativeQuery(
      """
      SELECT id, prison_document_id, event_type, court_document_type,
             document_generated_timestamp, ingestion_at
      FROM court_document
      $where
      ORDER BY random()
      LIMIT :limit
      """.trimIndent(),
    ).setParameter("limit", limit)
    if (eventType != null) query.setParameter("eventType", eventType)
    if (courtDocumentType != null) query.setParameter("courtDocumentType", courtDocumentType)

    @Suppress("UNCHECKED_CAST")
    val rows = query.resultList as List<Array<Any?>>
    return rows.map {
      CorpusRow(
        courtDocumentId = UUID.fromString(it[0].toString()),
        prisonDocumentId = UUID.fromString(it[1].toString()),
        eventType = it[2].toString(),
        courtDocumentType = it[3]?.toString(),
        documentGeneratedTimestamp = it[4]?.toString(),
        ingestionAt = it[5]?.toString(),
      )
    }
  }
}
