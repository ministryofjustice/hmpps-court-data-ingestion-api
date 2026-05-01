package uk.gov.justice.digital.hmpps.courtdataingestionapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import java.util.UUID

@Repository
interface CourtDocumentRepository : JpaRepository<CourtDocumentEntity, UUID> {
  fun countByDefendantId(defendantId: UUID): Long
  fun findFirstByDefendantId(defendantId: UUID): CourtDocumentEntity?
  fun findByDefendantIdIn(defendantIds: List<UUID>): List<CourtDocumentEntity>
  fun countByPrisonerNumber(prisonerNumber: String): Long
  fun findByPrisonerNumber(prisonerNumber: String): List<CourtDocumentEntity>
}
