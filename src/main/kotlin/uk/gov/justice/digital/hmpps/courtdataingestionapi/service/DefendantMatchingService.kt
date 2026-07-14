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

  fun matchPrisonerForDocument(courtDocumentEntity: CourtDocumentEntity): Boolean {
    val masterDefendantId = courtDocumentEntity.masterDefendantId
    val defendantId = matchDefendantId(masterDefendantId, caseReferencesOf(listOf(courtDocumentEntity)), populateOnMiss = true)
    val lookup = lookupPrisoner(defendantId, masterDefendantId)

    val prisonerNumber = lookup.matchedPrisonerNumber
    if (prisonerNumber == null) {
      saveOutcome(courtDocumentEntity, nonMatchOutcome(lookup))
      return false
    }

    createPrisonerMatch(courtDocumentEntity, prisonerNumber, matchOutcomeFor(defendantId))
    return true
  }

  fun matchPrisonerForMasterDefendant(masterDefendantId: UUID): Int {
    val documents = unmatchedDocumentsFor(masterDefendantId)
    if (documents.isEmpty()) return 0

    val defendantId = matchDefendantId(masterDefendantId, caseReferencesOf(documents), populateOnMiss = true)
    val lookup = lookupPrisoner(defendantId, masterDefendantId)

    val prisonerNumber = lookup.matchedPrisonerNumber
    if (prisonerNumber == null) {
      val outcome = nonMatchOutcome(lookup)
      documents.forEach { saveOutcome(it, outcome) }
      return 0
    }

    documents.forEach { createPrisonerMatch(it, prisonerNumber, matchOutcomeFor(defendantId)) }
    return documents.size
  }

  fun previewMatchPrisonerForMasterDefendant(masterDefendantId: UUID): PrisonerMatchPreview? {
    val documents = unmatchedDocumentsFor(masterDefendantId)
    if (documents.isEmpty()) return null

    val defendantId = matchDefendantId(masterDefendantId, caseReferencesOf(documents), populateOnMiss = false)
    val lookup = lookupPrisoner(defendantId, masterDefendantId)
    val outcome = if (lookup.result == PrisonerLookupResult.MATCHED) {
      matchOutcomeFor(defendantId)
    } else {
      nonMatchOutcome(lookup)
    }
    return PrisonerMatchPreview(outcome, documents.size)
  }

  fun matchDocumentsForPrisoner(prisonerNumber: String) {
    if (courtDocumentRepository.countByPrisonerNumber(prisonerNumber) > 0L) return

    val defendantIds = corePersonRecordService.findDefendantIdsByPrisonerNumber(prisonerNumber)
    if (defendantIds.isEmpty()) return

    val masterDefendantIds = courtCaseDefendantService.findMasterDefendantIds(defendantIds)
    courtDocumentRepository.findByMasterDefendantIdIn((masterDefendantIds + defendantIds).distinct())
      .forEach { createPrisonerMatch(it, prisonerNumber, MatchOutcome.MATCHED_ON_DEFENDANT_ID) }
  }

  private fun lookupPrisoner(defendantId: UUID?, masterDefendantId: UUID): PrisonerLookup = corePersonRecordService.findPrisonerByCommonPlatformId(defendantId ?: masterDefendantId)

  private fun matchOutcomeFor(defendantId: UUID?): MatchOutcome = if (defendantId != null) {
    MatchOutcome.MATCHED_ON_DEFENDANT_ID
  } else {
    MatchOutcome.MATCHED_ON_MASTER_DEFENDANT_ID
  }

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

  private fun matchDefendantId(
    masterDefendantId: UUID,
    caseReferences: List<String>,
    populateOnMiss: Boolean,
  ): UUID? = caseReferences.firstNotNullOfOrNull {
    matchDefendantId(masterDefendantId, it, populateOnMiss)
  }

  private fun matchDefendantId(
    masterDefendantId: UUID,
    caseReference: String,
    populateOnMiss: Boolean,
  ): UUID? {
    courtCaseDefendantService.findDefendantId(masterDefendantId, caseReference)?.let { return it }
    if (!populateOnMiss) return null
    return createDefendantMatch(masterDefendantId, caseReference)
  }

  private fun createDefendantMatch(masterDefendantId: UUID, caseReference: String): UUID? {
    val defendants = hmctsCourtDefendantApiClient.getDefendants(caseReference)
    defendants.forEach {
      courtCaseDefendantService.upsert(it.defendantId, caseReference, it.masterDefendantId, it.name, it.dateOfBirth)
    }
    return defendants.firstOrNull { it.masterDefendantId == masterDefendantId }?.defendantId
      ?: defendants.firstOrNull { it.defendantId == masterDefendantId }?.defendantId
  }

  private fun saveOutcome(courtDocumentEntity: CourtDocumentEntity, outcome: MatchOutcome) {
    courtDocumentEntity.matchOutcome = outcome
    courtDocumentRepository.save(courtDocumentEntity)
  }

  private fun createPrisonerMatch(
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

data class PrisonerMatchPreview(
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
