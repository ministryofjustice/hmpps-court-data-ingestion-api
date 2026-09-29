package uk.gov.justice.digital.hmpps.courtdataingestionapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtChargeEntity
import java.util.UUID

@Repository
interface CourtChargeRepository : JpaRepository<CourtChargeEntity, UUID>
