package uk.gov.justice.digital.hmpps.courtdataingestionapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtScheduleSnapshotEntity
import java.time.LocalDateTime
import java.util.UUID

@Repository
interface CourtScheduleSnapshotRepository : JpaRepository<CourtScheduleSnapshotEntity, UUID> {
  fun findFirstByCaseReferenceAndBodySha256IsNotNullOrderByFetchedAtDesc(caseReference: String): CourtScheduleSnapshotEntity?
  fun findByCaseReferenceOrderByFetchedAt(caseReference: String): List<CourtScheduleSnapshotEntity>

  @Query(
    value = """
      SELECT c.case_reference
      FROM (
        SELECT cdc.case_reference
        FROM court_document cd
        JOIN court_document_case cdc ON cdc.court_document_id = cd.id
        WHERE cd.addressed_prison IN (:prisons)
          AND cd.prisoner_number IS NOT NULL
          AND cd.ingestion_at >= :ingestedSince
        UNION
        SELECT latest.case_reference
        FROM (
          SELECT DISTINCT ON (s.case_reference) s.case_reference, s.body
          FROM court_schedule_snapshot s
          WHERE s.body IS NOT NULL
          ORDER BY s.case_reference, s.fetched_at DESC
        ) latest
        WHERE EXISTS (
          SELECT 1
          FROM jsonb_array_elements(coalesce(latest.body -> 'courtSchedule', CAST('[]' AS jsonb))) cs,
               jsonb_array_elements(coalesce(cs -> 'hearings', CAST('[]' AS jsonb))) h
          WHERE coalesce(
                  (SELECT max(CAST(coalesce(nullif(x ->> 'sittingEnd', ''), nullif(x ->> 'sittingStart', '')) AS timestamptz))
                   FROM jsonb_array_elements(coalesce(h -> 'courtSittings', CAST('[]' AS jsonb))) x),
                  CAST(CAST(nullif(h -> 'weekCommencing' ->> 'endDate', '') AS date) + 1 AS timestamptz)
                ) >= :listedUntilAfter
        )
      ) c
      WHERE c.case_reference > :afterCaseReference
        AND NOT EXISTS (
          SELECT 1
          FROM court_schedule_snapshot f
          WHERE f.case_reference = c.case_reference
            AND f.fetched_at >= :fetchedSince
        )
      ORDER BY c.case_reference
      LIMIT :limit
    """,
    nativeQuery = true,
  )
  fun findCaseReferencesToSnapshot(
    @Param("prisons") prisons: List<String>,
    @Param("ingestedSince") ingestedSince: LocalDateTime,
    @Param("listedUntilAfter") listedUntilAfter: LocalDateTime,
    @Param("afterCaseReference") afterCaseReference: String,
    @Param("fetchedSince") fetchedSince: LocalDateTime,
    @Param("limit") limit: Int,
  ): List<String>
}
