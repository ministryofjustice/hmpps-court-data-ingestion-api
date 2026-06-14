package uk.gov.justice.digital.hmpps.courtdataingestionapi.listener

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtDataIngestionService
import java.util.UUID

@Service
class CourtDataIngestionListener(
  private val objectMapper: ObjectMapper,
  private val courtDataIngestionService: CourtDataIngestionService,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(
    "courtdataingestion",
    factory = "hmppsQueueContainerFactoryProxy",
  )
  fun onMessage(
    rawMessage: String,
  ) {
    log.debug("Received message {}", rawMessage)
    val message = objectMapper.readValue<HmctsSubscriptionNotificationRequestBody>(rawMessage)
    courtDataIngestionService.receiveMessage(message)
  }
}

data class HmctsSubscriptionNotificationRequestBody(
  val cases: List<HmctsCase>,
  val masterDefendantId: UUID,
  val documentId: UUID,
  val documentGeneratedTimestamp: String,
  val prisonEmailAddress: String,
  val eventType: HmctsEventType = HmctsEventType.PRISON_COURT_REGISTER_GENERATED,
  val hearingId: UUID,
)

data class HmctsCase(
  val urn: String,
)
