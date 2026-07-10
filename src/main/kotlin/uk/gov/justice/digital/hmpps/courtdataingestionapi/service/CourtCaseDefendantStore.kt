package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtCaseDefendantEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtCaseDefendantRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Component
class CourtCaseDefendantStore(
  private val courtCaseDefendantRepository: CourtCaseDefendantRepository,
) {

  @Transactional
  fun upsert(
    defendantId: UUID,
    caseReference: String,
    masterDefendantId: UUID,
    name: String?,
    dateOfBirth: LocalDate?,
  ) {
    courtCaseDefendantRepository.save(
      CourtCaseDefendantEntity(
        defendantId = defendantId,
        caseReference = caseReference,
        masterDefendantId = masterDefendantId,
        name = name,
        dateOfBirth = dateOfBirth,
        retrievedAt = LocalDateTime.now(),
      ),
    )
  }
}
