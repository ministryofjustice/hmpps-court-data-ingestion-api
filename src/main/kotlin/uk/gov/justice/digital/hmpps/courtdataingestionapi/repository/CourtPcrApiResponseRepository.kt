package uk.gov.justice.digital.hmpps.courtdataingestionapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtPcrApiResponse
import java.util.UUID

@Repository
interface CourtPcrApiResponseRepository : JpaRepository<CourtPcrApiResponse, UUID>
