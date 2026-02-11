package uk.gov.justice.digital.hmpps.courtdataingestionapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.StartupLock

interface StartupLockRepository : JpaRepository<StartupLock, String>
