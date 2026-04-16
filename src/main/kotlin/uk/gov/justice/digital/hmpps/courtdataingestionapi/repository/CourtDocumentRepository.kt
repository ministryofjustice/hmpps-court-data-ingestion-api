package uk.gov.justice.digital.hmpps.courtdataingestionapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocument
import java.util.UUID

@Repository
interface CourtDocumentRepository : JpaRepository<CourtDocument, UUID> {
  fun countByDefendantId(defendantId: UUID): Long
  fun findFirstByDefendantId(defendantId: UUID): CourtDocument?
  fun findByDefendantIdIn(defendantIds: List<UUID>): List<CourtDocument>
  fun countByPrisonerNumber(prisonerNumber: String): Long
}
