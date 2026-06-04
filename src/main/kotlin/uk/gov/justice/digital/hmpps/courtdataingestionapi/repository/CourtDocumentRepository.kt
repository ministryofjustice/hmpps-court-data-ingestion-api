package uk.gov.justice.digital.hmpps.courtdataingestionapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import java.util.Optional
import java.util.UUID

@Repository
interface CourtDocumentRepository : JpaRepository<CourtDocumentEntity, UUID> {
  fun countByDefendantId(defendantId: UUID): Long
  fun findFirstByDefendantId(defendantId: UUID): CourtDocumentEntity?
  fun findByDefendantIdIn(defendantIds: List<UUID>): List<CourtDocumentEntity>
  fun countByPrisonerNumber(prisonerNumber: String): Long
  fun findByPrisonerNumber(prisonerNumber: String): List<CourtDocumentEntity>
  fun findByPrisonerNumberAndPrisonDocumentIdIn(personId: String, prisonDocumentIds: List<UUID>): List<CourtDocumentEntity>
  fun findFirstByPrisonDocumentId(prisonDocumentId: UUID): Optional<CourtDocumentEntity>
  fun findByExtractedTextSha256(hash: String): List<CourtDocumentEntity>
  fun findByDownloadedFileSha256(hash: String): List<CourtDocumentEntity>

  /**
   * Keyset page of documents that have not yet had their file hash populated, ordered by id so the
   * backfill visits every row exactly once and terminates even when a download fails (the row keeps
   * a null hash but the cursor still moves past its id). Pass the zero UUID to start.
   */
  @Query(
    value = """
      SELECT *
      FROM court_document
      WHERE id > :afterId
        AND downloaded_file_sha256 IS NULL
      ORDER BY id
      LIMIT :limit
    """,
    nativeQuery = true,
  )
  fun findUnhashedAfter(
    @Param("afterId") afterId: UUID,
    @Param("limit") limit: Int,
  ): List<CourtDocumentEntity>
}
