package uk.gov.justice.digital.hmpps.courtdataingestionapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.PrisonDocNotificationConfigEntity
import java.util.Optional

@Repository
interface PrisonDocNotificationConfigRepository : JpaRepository<PrisonDocNotificationConfigEntity, String> {
  fun findByPrisonId(prisonId: String): Optional<PrisonDocNotificationConfigEntity>
}
