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
import uk.gov.justice.digital.hmpps.courtdataingestionapi.listener.CourtDataIngestionEvent
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.IdentifiedWarrantFileRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.WarrantFileRepository
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsTopic
import uk.gov.justice.hmpps.sqs.publish

@Service
class CourtDataIngestionService(
  private val warrantFileRepository: WarrantFileRepository,
  private val identifiedWarrantFileRepository: IdentifiedWarrantFileRepository,
  private val corePersonApiClient: CorePersonApiClient,
  private val hmppsQueueService: HmppsQueueService,
  private val objectMapper: ObjectMapper,
) {
  private val eventTopic by lazy { hmppsQueueService.findByTopicId("domainevents") as HmppsTopic }

  @Transactional
  fun receiveMessage(message: CourtDataIngestionEvent) {
    val warrantFile = warrantFileRepository.save(
      WarrantFile(
        defendantId = message.defendantId,
        externalFileId = message.fileId,
      ),
    )
    val person = try {
      corePersonApiClient.getPerson(message.defendantId)
    } catch (e: WebClientResponseException) {
      if (HttpStatus.NOT_FOUND.isSameCodeAs(e.statusCode)) {
        return
      } else {
        throw e
      }
    }

    person.identifiers.prisonNumbers.forEach {
      identifiedWarrantFileRepository.save(
        IdentifiedWarrantFile(
          warrantFile = warrantFile,
          prisonerNumber = it,
        ),
      )
    }

    if (person.identifiers.prisonNumbers.isNotEmpty()) {
      eventTopic.publish(
        EVENT_TYPE,
        objectMapper.writeValueAsString(IdentifiedCourtWarrantEventPayload(person.identifiers.prisonNumbers, message.fileId)),
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
