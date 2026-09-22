package uk.gov.justice.digital.hmpps.courtdataingestionapi.repository

import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.DestinationType
import java.time.LocalDateTime
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
  fun findByIdGreaterThanAndDownloadedFileSha256IsNullOrderById(afterId: UUID, limit: Limit): List<CourtDocumentEntity>
  fun findByIdGreaterThanAndExtractedTextSha256IsNotNullOrderById(afterId: UUID, limit: Limit): List<CourtDocumentEntity>
  fun findByIdGreaterThanAndAddressedPrisonIsNullAndDeliveryMappingIdIsNullAndPrisonEmailAddressIsNotNullOrderById(
    afterId: UUID,
    limit: Limit,
  ): List<CourtDocumentEntity>

  @Query(
    """
      SELECT d.id FROM CourtDocumentEntity d
      WHERE d.courtHearing IS NULL
      AND d.ingestionAt > :ingestedAfter
      AND d.hmctsCourtHearingId IS NOT NULL
      AND d.id > :afterId
      ORDER BY d.id
    """,
  )
  fun findIdsWithUnpopulatedCourtHearingIngestedAfter(
    @Param("afterId") afterId: UUID,
    @Param("ingestedAfter") ingestedAfter: LocalDateTime,
    limit: Limit,
  ): List<UUID>

  @Query(
    """
      SELECT d.id FROM CourtDocumentEntity d
      WHERE d.id > :afterId
      AND (d.metadataVersion < :metadataVersion OR d.extractedTextSha256 IS NOT NULL)
      ORDER BY d.id
    """,
  )
  fun findUnmirroredIdsAfter(
    @Param("afterId") afterId: UUID,
    @Param("metadataVersion") metadataVersion: Int,
    limit: Limit,
  ): List<UUID>

  @Query(
    """
      SELECT DISTINCT d.masterDefendantId FROM CourtDocumentEntity d
      WHERE d.prisonerNumber IS NULL
      AND d.masterDefendantId > :afterId
      ORDER BY d.masterDefendantId
    """,
  )
  fun findUnmatchedMasterDefendantIdsAfter(@Param("afterId") afterId: UUID, limit: Limit): List<UUID>

  @Query(
    """
      SELECT DISTINCT d.id FROM CourtDocumentEntity d
      JOIN d.courtDocumentCases c
      WHERE d.id > :afterId
      AND c.caseReference LIKE '%,%'
      ORDER BY d.id
    """,
  )
  fun findIdsWithConcatenatedCaseReferencesAfter(@Param("afterId") afterId: UUID, limit: Limit): List<UUID>

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
    """
      UPDATE CourtDocumentEntity d
      SET d.addressedPrison = :addressedPrison,
          d.deliveryMappingId = :deliveryMappingId,
          d.deliverySource = COALESCE(:deliverySource, d.deliverySource)
      WHERE d.id = :id
    """,
  )
  fun applyDeliveryResolution(
    @Param("id") id: UUID,
    @Param("addressedPrison") addressedPrison: String?,
    @Param("deliveryMappingId") deliveryMappingId: UUID,
    @Param("deliverySource") deliverySource: DestinationType?,
  ): Int
}
