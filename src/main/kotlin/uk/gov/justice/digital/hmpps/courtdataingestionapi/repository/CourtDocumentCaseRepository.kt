package uk.gov.justice.digital.hmpps.courtdataingestionapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentCaseEntity
import java.util.UUID

@Repository
interface CourtDocumentCaseRepository : JpaRepository<CourtDocumentCaseEntity, UUID> {

  @Query(
    value = """
      SELECT DISTINCT case_reference
      FROM court_document_case
      WHERE case_reference > :afterCaseReference
      ORDER BY case_reference
      LIMIT :limit
    """,
    nativeQuery = true,
  )
  fun findDistinctCaseReferencesAfter(
    @Param("afterCaseReference") afterCaseReference: String,
    @Param("limit") limit: Int,
  ): List<String>
}
