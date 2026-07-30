package uk.gov.justice.digital.hmpps.courtdataingestionapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtCaseDefendantEntity
import java.util.UUID

@Repository
interface CourtCaseDefendantRepository : JpaRepository<CourtCaseDefendantEntity, UUID> {

  fun findAllByMasterDefendantId(masterDefendantId: UUID): List<CourtCaseDefendantEntity>

  fun findAllByMasterDefendantIdAndCaseReference(
    masterDefendantId: UUID,
    caseReference: String,
  ): List<CourtCaseDefendantEntity>
}
