package uk.gov.justice.digital.hmpps.courtdataingestionapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.ExtractionResultEntity
import java.time.Instant
import java.util.UUID

interface ExtractionResultRepository : JpaRepository<ExtractionResultEntity, UUID> {

  fun findByDocumentIdAndFormatIdAndFormatVersionAndExtractorVersion(
    documentId: UUID,
    formatId: String,
    formatVersion: Int,
    extractorVersion: String,
  ): ExtractionResultEntity?

  @Query(
    value = """
      SELECT DISTINCT cd.prison_document_id
      FROM court_document cd
      LEFT JOIN extraction_result e
        ON e.document_id = cd.prison_document_id
       AND e.format_id = :formatId
       AND e.format_version = :formatVersion
       AND e.extractor_version = :extractorVersion
      WHERE cd.ingestion_at >= :ingestedFrom
        AND (
          e.id IS NULL
          OR (e.status = 'ERROR' AND e.extracted_at < :retryErrorsBefore)
        )
      LIMIT :limit
    """,
    nativeQuery = true,
  )
  fun findDocumentIdsMissingExtraction(
    @Param("formatId") formatId: String,
    @Param("formatVersion") formatVersion: Int,
    @Param("extractorVersion") extractorVersion: String,
    @Param("ingestedFrom") ingestedFrom: Instant,
    @Param("retryErrorsBefore") retryErrorsBefore: Instant,
    @Param("limit") limit: Int,
  ): List<UUID>

  fun findByDocumentId(documentId: UUID): List<ExtractionResultEntity>
}
