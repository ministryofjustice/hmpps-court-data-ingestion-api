package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClientResponseException
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.CorePersonProvider
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmctsCourtDefendantApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentCaseEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.MatchOutcome
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionEnrichmentFlow
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.applyEnrichment
import uk.gov.justice.digital.hmpps.courtdataingestionapi.listener.HmctsSubscriptionNotificationRequestBody
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtCaseDefendantRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsTopic
import uk.gov.justice.hmpps.sqs.publish
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.String

@Service
@Transactional
class CourtDataIngestionService(
  private val ingestionEnrichmentFlow: IngestionEnrichmentFlow,
  private val courtDocumentRepository: CourtDocumentRepository,
  private val courtHearingService: CourtHearingService,
  private val corePersonApiClient: CorePersonProvider,
  private val hmppsQueueService: HmppsQueueService,
  private val objectMapper: ObjectMapper,
  private val fileService: FileService,
  private val hmctsCourtDefendantApiClient: HmctsCourtDefendantApiClient,
  private val courtCaseDefendantStore: CourtCaseDefendantStore,
  private val courtCaseDefendantRepository: CourtCaseDefendantRepository,
  @Value("\${extraction.mirror.metadata-version:0}")
  private val metadataVersion: Int,
) {
  private val eventTopic by lazy { hmppsQueueService.findByTopicId("domainevents") as HmppsTopic }

  fun receiveMessage(message: HmctsSubscriptionNotificationRequestBody) {
    val prisonDocument = fileService.ingestFile(message.documentId, message.eventType.documentType.documentApiType)

    val enriched = ingestionEnrichmentFlow.run(
      IngestionContext(
        prisonEmailAddress = message.prisonEmailAddress,
        prisonDocumentId = prisonDocument.documentUuid,
        hearingId = message.hearingId,
        caseReferences = message.cases.map { it.urn },
      ),
    )

    val courtDocumentEntity = courtDocumentRepository.save(
      CourtDocumentEntity(
        masterDefendantId = message.masterDefendantId,
        hmctsCourtDocumentId = message.documentId,
        prisonEmailAddress = message.prisonEmailAddress,
        documentGeneratedTimestamp = message.documentGeneratedTimestamp.withZoneSameInstant(ZoneId.of("Europe/London")).toLocalDateTime(),
        courtDocumentCases = message.cases.map { CourtDocumentCaseEntity(caseReference = it.urn) }.toMutableList(),
        prisonDocumentId = prisonDocument.documentUuid,
        eventType = message.eventType,
        courtDocumentType = message.eventType.documentType,
        hmctsCourtHearingId = message.hearingId,
      ).applyEnrichment(enriched),
    )

    mirrorEnrichmentToDocumentStore(courtDocumentEntity)

    courtHearingService.createOrUpdateCourtHearingData(courtDocumentEntity, enriched.hmtcsApiDataEnrichment)

    resolveAndMatch(courtDocumentEntity, message.masterDefendantId, message.cases.map { it.urn })
  }

  private fun mirrorEnrichmentToDocumentStore(courtDocumentEntity: CourtDocumentEntity) {
    val mirrorOutcome = runCatching {
      fileService.mirrorEnrichmentToDocumentStore(courtDocumentEntity)
    }.getOrElse {
      log.warn("Failed to mirror enrichment to document store for {}", courtDocumentEntity.prisonDocumentId, it)
      null
    }

    if (mirrorOutcome?.fullySuccessful == true) {
      courtDocumentEntity.metadataVersion = metadataVersion
      courtDocumentEntity.mirroredToDocStoreAt = LocalDateTime.now()
      courtDocumentRepository.save(courtDocumentEntity)
    }
  }

  fun attemptToMatchForNewPrisoner(prisonerNumber: String) {
    val previouslyIdentified = courtDocumentRepository.countByPrisonerNumber(prisonerNumber)
    if (previouslyIdentified == 0L) {
      val person = corePersonApiClient.getPersonByPrisonerNumber(prisonerNumber)
      if (person?.identifiers?.defendantIds?.isNotEmpty() == true) {
        val files =
          courtDocumentRepository.findByMasterDefendantIdIn(person.identifiers.defendantIds.map { UUID.fromString(it) })
        files.forEach {
          createMatch(it, prisonerNumber, MatchOutcome.MATCHED_ON_DEFENDANT_ID)
        }
      }
    }
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

  fun resolveAndMatch(
    courtDocumentEntity: CourtDocumentEntity,
    masterDefendantId: UUID,
    caseReferences: List<String>,
  ): Boolean {
    val resolvedDefendantId = resolveDefendantId(masterDefendantId, caseReferences, populateOnMiss = true)
    val commonPlatformId = resolvedDefendantId ?: masterDefendantId
    val matchOutcomeOnMatch =
      if (resolvedDefendantId != null) MatchOutcome.MATCHED_ON_DEFENDANT_ID else MatchOutcome.MATCHED_ON_MASTER_DEFENDANT_ID

    val person = try {
      corePersonApiClient.getPersonByCommonPlatformId(commonPlatformId)
    } catch (e: WebClientResponseException) {
      if (HttpStatus.NOT_FOUND.isSameCodeAs(e.statusCode)) {
        courtDocumentEntity.matchOutcome = MatchOutcome.NO_CORE_PERSON
        courtDocumentRepository.save(courtDocumentEntity)
        return false
      } else {
        throw e
      }
    }

    return if (person.identifiers.prisonNumbers.size == 1) {
      createMatch(courtDocumentEntity, person.identifiers.prisonNumbers[0], matchOutcomeOnMatch)
      true
    } else {
      courtDocumentEntity.matchOutcome = if (person.identifiers.prisonNumbers.size > 1) {
        log.info("Found more than one prisonNumber from core person: ${person.identifiers.prisonNumbers}")
        MatchOutcome.MULTIPLE_PRISON_NUMBERS
      } else {
        MatchOutcome.NO_PRISON_NUMBER
      }
      courtDocumentRepository.save(courtDocumentEntity)
      false
    }
  }

  fun reattemptMatchForMaster(masterDefendantId: UUID): Int {
    val documents = unmatchedDocumentsFor(masterDefendantId)
    if (documents.isEmpty()) return 0

    val resolvedDefendantId = resolveDefendantId(masterDefendantId, caseReferencesOf(documents), populateOnMiss = true)
    val commonPlatformId = resolvedDefendantId ?: masterDefendantId
    val matchOutcomeOnMatch =
      if (resolvedDefendantId != null) MatchOutcome.MATCHED_ON_DEFENDANT_ID else MatchOutcome.MATCHED_ON_MASTER_DEFENDANT_ID

    val person = try {
      corePersonApiClient.getPersonByCommonPlatformId(commonPlatformId)
    } catch (e: WebClientResponseException) {
      if (HttpStatus.NOT_FOUND.isSameCodeAs(e.statusCode)) {
        documents.forEach { recordOutcome(it, MatchOutcome.NO_CORE_PERSON) }
        return 0
      } else {
        throw e
      }
    }

    return when {
      person.identifiers.prisonNumbers.size == 1 -> {
        documents.forEach { createMatch(it, person.identifiers.prisonNumbers[0], matchOutcomeOnMatch) }
        documents.size
      }
      person.identifiers.prisonNumbers.size > 1 -> {
        documents.forEach { recordOutcome(it, MatchOutcome.MULTIPLE_PRISON_NUMBERS) }
        0
      }
      else -> {
        documents.forEach { recordOutcome(it, MatchOutcome.NO_PRISON_NUMBER) }
        0
      }
    }
  }

  /**
   * Read-only preview of what reanchoring a master would record, using only the current store (no
   * HMCTS fetch, no writes). Returns the outcome and how many unmatched documents it covers, or null
   * if the master has none. Used by the reanchor dry-run backfill.
   */
  fun previewReattemptMatchForMaster(masterDefendantId: UUID): ReanchorPreview? {
    val documents = unmatchedDocumentsFor(masterDefendantId)
    if (documents.isEmpty()) return null

    val resolvedDefendantId = resolveDefendantId(masterDefendantId, caseReferencesOf(documents), populateOnMiss = false)
    val commonPlatformId = resolvedDefendantId ?: masterDefendantId
    val outcome = try {
      val person = corePersonApiClient.getPersonByCommonPlatformId(commonPlatformId)
      when {
        person.identifiers.prisonNumbers.size == 1 ->
          if (resolvedDefendantId != null) MatchOutcome.MATCHED_ON_DEFENDANT_ID else MatchOutcome.MATCHED_ON_MASTER_DEFENDANT_ID
        person.identifiers.prisonNumbers.size > 1 -> MatchOutcome.MULTIPLE_PRISON_NUMBERS
        else -> MatchOutcome.NO_PRISON_NUMBER
      }
    } catch (e: WebClientResponseException) {
      if (HttpStatus.NOT_FOUND.isSameCodeAs(e.statusCode)) MatchOutcome.NO_CORE_PERSON else throw e
    }
    return ReanchorPreview(outcome, documents.size)
  }

  private fun unmatchedDocumentsFor(masterDefendantId: UUID): List<CourtDocumentEntity> = courtDocumentRepository.findByMasterDefendantIdIn(listOf(masterDefendantId)).filter { it.prisonerNumber == null }

  private fun caseReferencesOf(documents: List<CourtDocumentEntity>): List<String> = documents.flatMap { document -> document.courtDocumentCases.map { it.caseReference } }.distinct()

  private fun recordOutcome(courtDocumentEntity: CourtDocumentEntity, outcome: MatchOutcome) {
    courtDocumentEntity.matchOutcome = outcome
    courtDocumentRepository.save(courtDocumentEntity)
  }

  private fun resolveDefendantId(
    masterDefendantId: UUID,
    caseReferences: List<String>,
    populateOnMiss: Boolean,
  ): UUID? = caseReferences
    .map { ensureStoredAndResolve(masterDefendantId, it, populateOnMiss) }
    .filterNotNull()
    .firstOrNull()

  private fun ensureStoredAndResolve(
    masterDefendantId: UUID,
    caseReference: String,
    populateOnMiss: Boolean,
  ): UUID? {
    courtCaseDefendantRepository.findByMasterDefendantIdAndCaseReference(masterDefendantId, caseReference)
      ?.let { return it.defendantId }
    if (!populateOnMiss) return null

    val defendants = hmctsCourtDefendantApiClient.getDefendants(caseReference, masterDefendantId = masterDefendantId)
    defendants.forEach {
      courtCaseDefendantStore.upsert(it.defendantId, caseReference, it.masterDefendantId, it.name, it.dateOfBirth)
    }
    return defendants.singleOrNull()?.defendantId
  }

  companion object {
    const val EVENT_TYPE = "court-document.file.received"
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

data class ReanchorPreview(
  val outcome: MatchOutcome,
  val documentCount: Int,
)

data class IdentifiedCourtWarrantEventPayload(
  val eventType: String = CourtDataIngestionService.EVENT_TYPE,
  val additionalInformation: IdentifiedCourtWarrantAdditionalInformation,
)

data class IdentifiedCourtWarrantAdditionalInformation(
  val courtDocumentId: UUID,
  val prisonDocumentId: UUID,
  val prisonerNumber: String,
)
