package uk.gov.justice.digital.hmpps.courtdataingestionapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@Repository
interface CourtDocumentRepository : JpaRepository<CourtDocumentEntity, UUID> {
  fun countByMasterDefendantId(masterDefendantId: UUID): Long
  fun findFirstByMasterDefendantIdOrderByIngestionAtDesc(masterDefendantId: UUID): CourtDocumentEntity?
  fun findByMasterDefendantIdIn(masterDefendantIds: List<UUID>): List<CourtDocumentEntity>
  fun countByPrisonerNumber(prisonerNumber: String): Long
  fun findByPrisonerNumber(prisonerNumber: String): List<CourtDocumentEntity>
  fun findByPrisonerNumberAndPrisonDocumentIdIn(personId: String, prisonDocumentIds: List<UUID>): List<CourtDocumentEntity>
  fun findFirstByPrisonDocumentId(prisonDocumentId: UUID): Optional<CourtDocumentEntity>

  @Query(
    value = """
      SELECT *
      FROM court_document
      WHERE court_hearing_id IS NULL
      AND hmcts_court_hearing_id IS NOT NULL
      AND id > :afterId
      ORDER BY id
      LIMIT :limit
    """,
    nativeQuery = true,
  )
  fun findUnpopulatedCourtHearingData(
    @Param("afterId") afterId: UUID,
    @Param("limit") limit: Int,
  ): List<CourtDocumentEntity>

  @Query(
    value = """
      SELECT id
      FROM court_document
      WHERE court_hearing_id IS NULL
      AND ingestion_at > :ingestedAfter
      AND hmcts_court_hearing_id IS NOT NULL
      AND id > :afterId
      ORDER BY id
      LIMIT :limit
    """,
    nativeQuery = true,
  )
  fun findUnpopulatedCourtHearingDataIngestedAfter(
    @Param("afterId") afterId: UUID,
    @Param("ingestedAfter") ingestedAfter: LocalDate,
    @Param("limit") limit: Int,
  ): List<UUID>

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

  @Query(
    value = """
      SELECT *
      FROM court_document
      WHERE id > :afterId
        AND extracted_text_sha256 IS NOT NULL
      ORDER BY id
      LIMIT :limit
    """,
    nativeQuery = true,
  )
  fun findHashedAfter(
    @Param("afterId") afterId: UUID,
    @Param("limit") limit: Int,
  ): List<CourtDocumentEntity>

  @Query(
    value = """
      SELECT *
      FROM court_document
      WHERE id > :afterId
        AND (metadata_version < :metadataVersion
          OR extracted_text_sha256 IS NOT NULL)
      ORDER BY id
      LIMIT :limit
    """,
    nativeQuery = true,
  )
  fun findUnmirroredAfter(
    @Param("afterId") afterId: UUID,
    @Param("metadataVersion") metadataVersion: Int,
    @Param("limit") limit: Int,
  ): List<CourtDocumentEntity>

  @Query(
    value = """
      SELECT DISTINCT master_defendant_id
      FROM court_document
      WHERE prisoner_number IS NULL
      AND master_defendant_id > :afterId
      ORDER BY master_defendant_id
      LIMIT :limit
    """,
    nativeQuery = true,
  )
  fun findUnmatchedMasterDefendantIdsAfter(
    @Param("afterId") afterId: UUID,
    @Param("limit") limit: Int,
  ): List<UUID>

  @Query(
    value = """
      SELECT distinct d.id
      FROM Court_Document d
      WHERE d.id > :afterId
        AND d.id IN (SELECT t.court_document_id FROM Court_Document_Case t WHERE t.case_reference LIKE '%,%')
      ORDER BY d.id
      LIMIT :limit
    """,
    nativeQuery = true,
  )
  fun findCourtDocumentIdsWithConcatenatedCaseReferencesAfter(
    @Param("afterId") afterId: UUID,
    @Param("limit") limit: Int,
  ): List<UUID>
}
