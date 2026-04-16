package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClientResponseException
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.CorePersonApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocument
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentCase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.listener.HmctsSubscriptionNotificationRequestBody
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsTopic
import uk.gov.justice.hmpps.sqs.publish
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional
class CourtDataIngestionService(
  private val courtDocumentRepository: CourtDocumentRepository,
  private val corePersonApiClient: CorePersonApiClient,
  private val hmppsQueueService: HmppsQueueService,
  private val objectMapper: ObjectMapper,
  private val fileService: FileService,
) {
  private val eventTopic by lazy { hmppsQueueService.findByTopicId("domainevents") as HmppsTopic }

  fun receiveMessage(message: HmctsSubscriptionNotificationRequestBody) {
    val prisonDocument = fileService.ingestFile(message.documentId)
    val courtDocument = courtDocumentRepository.save(
      CourtDocument(
        defendantId = message.masterDefendantId,
        courtDocumentId = message.documentId,
        prisonEmailAddress = message.prisonEmailAddress,
        documentGeneratedTimestamp = message.documentGeneratedTimestamp,
        courtDocumentCases = message.cases.map { CourtDocumentCase(caseReference = it.urn) },
        prisonDocumentId = prisonDocument.documentUuid,
      ),
    )
    val person = try {
      corePersonApiClient.getPersonByCommonPlatformId(message.masterDefendantId)
    } catch (e: WebClientResponseException) {
      if (HttpStatus.NOT_FOUND.isSameCodeAs(e.statusCode)) {
        return
      } else {
        throw e
      }
    }

    if (person.identifiers.prisonNumbers.size == 1) {
      createMatch(courtDocument, person.identifiers.prisonNumbers[0])
    } else if (person.identifiers.prisonNumbers.size > 1) {
      log.info("Found more than one prisonNumber from core person: ${person.identifiers.prisonNumbers}")
    }
  }

  fun attemptToMatchForNewPrisoner(prisonerNumber: String) {
    val previouslyIdentified = courtDocumentRepository.countByPrisonerNumber(prisonerNumber)
    if (previouslyIdentified == 0L) {
      val person = corePersonApiClient.getPersonByPrisonerNumber(prisonerNumber)
      if (person?.identifiers?.defendantIds?.isNotEmpty() == true) {
        val files =
          courtDocumentRepository.findByDefendantIdIn(person.identifiers.defendantIds.map { UUID.fromString(it) })
        files.forEach {
          createMatch(it, prisonerNumber)
        }
      }
    }
  }

  private fun createMatch(courtDocument: CourtDocument, prisonerNumber: String) {
    courtDocument.prisonerNumber = prisonerNumber
    courtDocument.ingestionAt = LocalDateTime.now()
    courtDocumentRepository.save(courtDocument)

    fileService.setPrisonerId(courtDocument.prisonDocumentId, prisonerNumber)
    val payload = IdentifiedCourtWarrantEventPayload(
      courtDocumentId = courtDocument.courtDocumentId,
      prisonDocumentId = courtDocument.prisonDocumentId,
      prisonerNumber = prisonerNumber,
    )

    eventTopic.publish(
      EVENT_TYPE,
      objectMapper.writeValueAsString(payload),
      attributes = mapOf(
        "type" to MessageAttributeValue.builder().dataType("String").stringValue(EVENT_TYPE).build(),
      ),
    )
  }

  companion object {
    private const val EVENT_TYPE = "court-document.file.received"
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

data class IdentifiedCourtWarrantEventPayload(
  val courtDocumentId: UUID,
  val prisonDocumentId: UUID,
  val prisonerNumber: String,
)
