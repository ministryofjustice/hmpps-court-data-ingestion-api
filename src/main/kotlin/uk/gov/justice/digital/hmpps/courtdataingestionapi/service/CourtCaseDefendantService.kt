package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtCaseDefendantEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtCaseDefendantRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Component
class CourtCaseDefendantService(
  private val courtCaseDefendantRepository: CourtCaseDefendantRepository,
) {

  fun findDefendantId(masterDefendantId: UUID, caseReference: String): UUID? = courtCaseDefendantRepository.findByMasterDefendantIdAndCaseReference(masterDefendantId, caseReference)?.defendantId

  fun findMasterDefendantIds(defendantIds: List<UUID>): List<UUID> = courtCaseDefendantRepository.findAllById(defendantIds).map { it.masterDefendantId }

  @Transactional
  fun upsert(
    defendantId: UUID,
    caseReference: String,
    masterDefendantId: UUID,
    name: String?,
    dateOfBirth: LocalDate?,
  ) {
    val existing = courtCaseDefendantRepository.findById(defendantId).orElse(null)
    // Changed Master Defendant Id in record
    if (existing != null) {
      if (existing.masterDefendantId != masterDefendantId) {
        log.warn(
          "Defendant {} moved master {} -> {} (case {}); upstream merge or correction",
          defendantId,
          existing.masterDefendantId,
          masterDefendantId,
          caseReference,
        )
      }
      // Changed Name or DOB
      if (existing.name != name || existing.dateOfBirth != dateOfBirth) {
        log.warn("Identity changed upstream for defendant {} on case {}", defendantId, caseReference)
      }
    }

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

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
