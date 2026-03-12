package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClientResponseException
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.CorePersonApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.IdentifiedWarrantFile
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.WarrantFile
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.WarrantFileCase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.listener.HmctsSubscriptionNotificationRequestBody
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.IdentifiedWarrantFileRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.WarrantFileRepository
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsTopic
import uk.gov.justice.hmpps.sqs.publish
import java.util.UUID

@Service
@Transactional
class CourtDataIngestionService(
  private val warrantFileRepository: WarrantFileRepository,
  private val identifiedWarrantFileRepository: IdentifiedWarrantFileRepository,
  private val corePersonApiClient: CorePersonApiClient,
  private val hmppsQueueService: HmppsQueueService,
  private val objectMapper: ObjectMapper,
) {
  private val eventTopic by lazy { hmppsQueueService.findByTopicId("domainevents") as HmppsTopic }

  fun receiveMessage(message: HmctsSubscriptionNotificationRequestBody) {
    val warrantFile = warrantFileRepository.save(
      WarrantFile(
        defendantId = message.masterDefendantId,
        externalFileId = message.documentId,
        defendantName = message.defendantName,
        prisonEmailAddress = message.prisonEmailAddress,
        defendantDateOfBirth = message.defendantDateOfBirth,
        documentGeneratedTimestamp = message.documentGeneratedTimestamp,
        warrantFileCases = message.cases.map { WarrantFileCase(caseReference = it.urn) },
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

    createMatches(warrantFile, person.identifiers.prisonNumbers)
  }

  fun attemptToMatchForNewPrisoner(prisonerNumber: String) {
    val previouslyIdentified = identifiedWarrantFileRepository.countByPrisonerNumber(prisonerNumber)
    if (previouslyIdentified == 0L) {
      val person = corePersonApiClient.getPersonByPrisonerNumber(prisonerNumber)
      if (person.identifiers.defendantIds.isNotEmpty()) {
        val files =
          warrantFileRepository.findByDefendantIdIn(person.identifiers.defendantIds.map { UUID.fromString(it) })
        files.forEach {
          createMatches(it, listOf(prisonerNumber))
        }
      }
    }
  }

  private fun createMatches(warrantFile: WarrantFile, prisonerNumbers: List<String>) {
    prisonerNumbers.forEach {
      identifiedWarrantFileRepository.save(
        IdentifiedWarrantFile(
          warrantFile = warrantFile,
          prisonerNumber = it,
        ),
      )
    }

    if (prisonerNumbers.isNotEmpty()) {
      eventTopic.publish(
        EVENT_TYPE,
        objectMapper.writeValueAsString(
          IdentifiedCourtWarrantEventPayload(
            prisonerNumbers,
            warrantFile.externalFileId,
          ),
        ),
        attributes = mapOf(
          "type" to MessageAttributeValue.builder().dataType("String").stringValue(EVENT_TYPE).build(),
        ),
      )
    }
  }

  companion object {
    private const val EVENT_TYPE = "court-warrant.file.received"
  }
}

data class IdentifiedCourtWarrantEventPayload(
  val prisonerNumbers: List<String>,
  val filename: String,
)
