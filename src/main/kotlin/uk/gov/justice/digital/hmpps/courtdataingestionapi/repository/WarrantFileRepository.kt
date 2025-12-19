package uk.gov.justice.digital.hmpps.courtdataingestionapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.WarrantFile
import java.util.UUID

@Repository
interface WarrantFileRepository : JpaRepository<WarrantFile, UUID> {
  fun countByDefendantId(defendantId: UUID): Long
  fun findFirstByDefendantId(defendantId: UUID): WarrantFile?
}
