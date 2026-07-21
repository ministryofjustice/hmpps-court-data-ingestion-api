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
      SELECT *
      FROM court_document d
      WHERE id > :afterId
        AND EXISTS(SELECT 1 FROM court_hearing h WHERE h.id = d.court_hearing_id AND h.court_code IS NULL)
      ORDER BY id
      LIMIT :limit
    """,
    nativeQuery = true,
  )
  fun findUnpopulatedCourtRegisterData(
    @Param("afterId") afterId: UUID,
    @Param("limit") limit: Int,
  ): List<CourtDocumentEntity>

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
}
