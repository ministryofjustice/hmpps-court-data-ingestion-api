package uk.gov.justice.digital.hmpps.courtdataingestionapi.listener

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.messaging.MessageHeaders
import org.springframework.messaging.handler.annotation.Headers
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.courtdataingestionapi.config.WebClientConfiguration.Companion.X_CORRELATION_ID_HEADER
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtDataIngestionService
import java.time.LocalDateTime
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
    @Headers headers: MessageHeaders
  ) {
    try {
      log.debug("Received message {}", rawMessage)
      val message = objectMapper.readValue<HmctsSubscriptionNotificationRequestBody>(rawMessage)
      courtDataIngestionService.receiveMessage(message)
    } finally {
      MDC.remove(X_CORRELATION_ID_HEADER)
    }
  }


}

data class HmctsSubscriptionNotificationRequestBody(
  val cases: List<HmctsCase>,
  val masterDefendantId: UUID,
  val documentId: UUID,
  val documentGeneratedTimestamp: LocalDateTime,
  val prisonEmailAddress: String,
  // TODO Default to PCR until this is released to production on hmcts side.
  val eventType: HmctsEventType = HmctsEventType.PRISON_COURT_REGISTER_GENERATED,
)

data class HmctsCase(
  val urn: String,
)
