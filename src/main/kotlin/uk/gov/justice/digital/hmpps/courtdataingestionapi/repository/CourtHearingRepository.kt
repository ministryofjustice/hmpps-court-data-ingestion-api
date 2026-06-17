package uk.gov.justice.digital.hmpps.courtdataingestionapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtHearingEntity
import java.util.UUID

@Repository
interface CourtHearingRepository : JpaRepository<CourtHearingEntity, UUID> {
  fun findFirstByHmctsCourtHearingId(hmctsCourtHearingId: UUID): CourtHearingEntity?
}
