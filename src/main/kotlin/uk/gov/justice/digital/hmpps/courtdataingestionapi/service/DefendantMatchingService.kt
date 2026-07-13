package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmctsCourtDefendantApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.MatchOutcome
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsTopic
import uk.gov.justice.hmpps.sqs.publish
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional
class DefendantMatchingService(
  private val courtDocumentRepository: CourtDocumentRepository,
  private val corePersonRecordService: CorePersonRecordService,
  private val hmctsCourtDefendantApiClient: HmctsCourtDefendantApiClient,
  private val courtCaseDefendantService: CourtCaseDefendantService,
  private val fileService: FileService,
  private val hmppsQueueService: HmppsQueueService,
  private val objectMapper: ObjectMapper,
) {
  private val eventTopic by lazy { hmppsQueueService.findByTopicId("domainevents") as HmppsTopic }

  fun resolveDefendantAndMatchPrisoner(
    courtDocumentEntity: CourtDocumentEntity,
    masterDefendantId: UUID,
    caseReferences: List<String>,
  ): Boolean {
    val resolvedDefendantId = resolveDefendantId(masterDefendantId, caseReferences, populateOnMiss = true)
    val lookup = lookupPrisoner(resolvedDefendantId, masterDefendantId)

    val prisonerNumber = matchedPrisonerNumber(lookup)
    if (prisonerNumber == null) {
      recordOutcome(courtDocumentEntity, nonMatchOutcome(lookup))
      return false
    }

    createMatch(courtDocumentEntity, prisonerNumber, matchOutcomeFor(resolvedDefendantId))
    return true
  }

  fun resolveDefendantForMasterDefendant(masterDefendantId: UUID): Int {
    val documents = unmatchedDocumentsFor(masterDefendantId)
    if (documents.isEmpty()) return 0

    val resolvedDefendantId = resolveDefendantId(masterDefendantId, caseReferencesOf(documents), populateOnMiss = true)
    val lookup = lookupPrisoner(resolvedDefendantId, masterDefendantId)

    val prisonerNumber = matchedPrisonerNumber(lookup)
    if (prisonerNumber == null) {
      val outcome = nonMatchOutcome(lookup)
      documents.forEach { recordOutcome(it, outcome) }
      return 0
    }

    documents.forEach { createMatch(it, prisonerNumber, matchOutcomeFor(resolvedDefendantId)) }
    return documents.size
  }

  fun previewResolveDefendantForMasterDefendant(masterDefendantId: UUID): DefendantResolutionPreview? {
    val documents = unmatchedDocumentsFor(masterDefendantId)
    if (documents.isEmpty()) return null

    val resolvedDefendantId = resolveDefendantId(masterDefendantId, caseReferencesOf(documents), populateOnMiss = false)
    val lookup = lookupPrisoner(resolvedDefendantId, masterDefendantId)
    val outcome = if (lookup.result == PrisonerLookupResult.MATCHED) {
      matchOutcomeFor(resolvedDefendantId)
    } else {
      nonMatchOutcome(lookup)
    }
    return DefendantResolutionPreview(outcome, documents.size)
  }

  fun attemptToMatchForNewPrisoner(prisonerNumber: String) {
    if (courtDocumentRepository.countByPrisonerNumber(prisonerNumber) > 0L) return

    val defendantIds = corePersonRecordService.findDefendantIdsByPrisonerNumber(prisonerNumber)
    if (defendantIds.isEmpty()) return

    val resolvedMasterIds = courtCaseDefendantService.findMasterDefendantIds(defendantIds)
    courtDocumentRepository.findByMasterDefendantIdIn((resolvedMasterIds + defendantIds).distinct())
      .forEach { createMatch(it, prisonerNumber, MatchOutcome.MATCHED_ON_DEFENDANT_ID) }
  }

  private fun lookupPrisoner(resolvedDefendantId: UUID?, masterDefendantId: UUID): PrisonerLookup = corePersonRecordService.findPrisonerByCommonPlatformId(resolvedDefendantId ?: masterDefendantId)

  private fun matchOutcomeFor(resolvedDefendantId: UUID?): MatchOutcome = if (resolvedDefendantId != null) {
    MatchOutcome.MATCHED_ON_DEFENDANT_ID
  } else {
    MatchOutcome.MATCHED_ON_MASTER_DEFENDANT_ID
  }

  private fun matchedPrisonerNumber(lookup: PrisonerLookup): String? = if (lookup.result == PrisonerLookupResult.MATCHED) lookup.prisonerNumber else null

  private fun nonMatchOutcome(lookup: PrisonerLookup): MatchOutcome = when (lookup.result) {
    PrisonerLookupResult.NO_CORE_PERSON -> MatchOutcome.NO_CORE_PERSON
    PrisonerLookupResult.NO_PRISON_NUMBER -> MatchOutcome.NO_PRISON_NUMBER
    PrisonerLookupResult.MULTIPLE_PRISON_NUMBERS -> {
      log.info("Found more than one prisonNumber from core person: {}", lookup.prisonerNumbers)
      MatchOutcome.MULTIPLE_PRISON_NUMBERS
    }
    PrisonerLookupResult.MATCHED -> MatchOutcome.MATCHED_ON_MASTER_DEFENDANT_ID
  }

  private fun unmatchedDocumentsFor(masterDefendantId: UUID): List<CourtDocumentEntity> = courtDocumentRepository.findByMasterDefendantIdIn(listOf(masterDefendantId)).filter { it.prisonerNumber == null }

  private fun caseReferencesOf(documents: List<CourtDocumentEntity>): List<String> = documents.flatMap { document -> document.courtDocumentCases.map { it.caseReference } }.distinct()

  private fun resolveDefendantId(
    masterDefendantId: UUID,
    caseReferences: List<String>,
    populateOnMiss: Boolean,
  ): UUID? = caseReferences.firstNotNullOfOrNull {
    ensureStoredAndResolveDefendantId(masterDefendantId, it, populateOnMiss)
  }

  private fun ensureStoredAndResolveDefendantId(
    masterDefendantId: UUID,
    caseReference: String,
    populateOnMiss: Boolean,
  ): UUID? {
    courtCaseDefendantService.findDefendantId(masterDefendantId, caseReference)
      ?.let { return it }
    if (!populateOnMiss) return null

    val defendants = hmctsCourtDefendantApiClient.getDefendants(caseReference)
    defendants.forEach {
      courtCaseDefendantService.upsert(it.defendantId, caseReference, it.masterDefendantId, it.name, it.dateOfBirth)
    }
    return defendants.firstOrNull { it.masterDefendantId == masterDefendantId }?.defendantId
      ?: defendants.firstOrNull { it.defendantId == masterDefendantId }?.defendantId
  }

  private fun recordOutcome(courtDocumentEntity: CourtDocumentEntity, outcome: MatchOutcome) {
    courtDocumentEntity.matchOutcome = outcome
    courtDocumentRepository.save(courtDocumentEntity)
  }

  private fun createMatch(
    courtDocumentEntity: CourtDocumentEntity,
    prisonerNumber: String,
    matchOutcome: MatchOutcome,
  ) {
    courtDocumentEntity.prisonerNumber = prisonerNumber
    courtDocumentEntity.identifiedAt = LocalDateTime.now()
    courtDocumentEntity.matchOutcome = matchOutcome
    courtDocumentRepository.save(courtDocumentEntity)

    fileService.setPrisonerId(courtDocumentEntity.prisonDocumentId, prisonerNumber)
    val payload = IdentifiedCourtWarrantEventPayload(
      additionalInformation = IdentifiedCourtWarrantAdditionalInformation(
        courtDocumentId = courtDocumentEntity.hmctsCourtDocumentId,
        prisonDocumentId = courtDocumentEntity.prisonDocumentId,
        prisonerNumber = prisonerNumber,
      ),
    )

    try {
      eventTopic.publish(
        EVENT_TYPE,
        objectMapper.writeValueAsString(payload),
        attributes = mapOf(
          "type" to MessageAttributeValue.builder().dataType("String").stringValue(EVENT_TYPE).build(),
        ),
      )
    } catch (e: Exception) {
      log.error("Error publishing event", e)
    }
  }

  companion object {
    const val EVENT_TYPE = "court-document.file.received"
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

data class DefendantResolutionPreview(
  val outcome: MatchOutcome,
  val documentCount: Int,
)

data class IdentifiedCourtWarrantEventPayload(
  val eventType: String = DefendantMatchingService.EVENT_TYPE,
  val additionalInformation: IdentifiedCourtWarrantAdditionalInformation,
)

data class IdentifiedCourtWarrantAdditionalInformation(
  val courtDocumentId: UUID,
  val prisonDocumentId: UUID,
  val prisonerNumber: String,
)
