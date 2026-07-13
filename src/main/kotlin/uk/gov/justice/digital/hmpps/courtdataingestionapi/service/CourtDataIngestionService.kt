package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentCaseEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionEnrichmentFlow
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.applyEnrichment
import uk.gov.justice.digital.hmpps.courtdataingestionapi.listener.HmctsSubscriptionNotificationRequestBody
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Ingests a court document notification: fetch the file, enrich it, persist it, mirror the
 * enrichment to the document store, record hearing data, then hand off to [DefendantMatchingService]
 * to work out who the document is about.
 */
@Service
@Transactional
class CourtDataIngestionService(
  private val ingestionEnrichmentFlow: IngestionEnrichmentFlow,
  private val courtDocumentRepository: CourtDocumentRepository,
  private val courtHearingService: CourtHearingService,
  private val fileService: FileService,
  private val defendantMatchingService: DefendantMatchingService,
  @Value("\${extraction.mirror.metadata-version:0}")
  private val metadataVersion: Int,
) {

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

    defendantMatchingService.resolveDefendantAndMatchPrisoner(
      courtDocumentEntity,
      message.masterDefendantId,
      message.cases.map { it.urn },
    )
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

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
