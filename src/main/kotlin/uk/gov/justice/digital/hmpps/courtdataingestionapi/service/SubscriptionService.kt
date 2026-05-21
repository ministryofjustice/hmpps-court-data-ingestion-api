package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmctsSubscriptionApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.Subscription
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.NotificationEndpoint
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.SubscriptionRequest
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.SubscriptionRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.subscription.SubscriptionCallbackConfig
import java.time.LocalDateTime
import java.util.UUID

@Service
class SubscriptionService(
  private val subscriptionRepository: SubscriptionRepository,
  private val hmctsSubscriptionApiClient: HmctsSubscriptionApiClient,
  private val subscriptionCallbackConfig: SubscriptionCallbackConfig,
  private val secretsManagerService: SecretsManagerService,
) {

  @Transactional
  fun subscribe(xCorrelationId: UUID) {
    val subscription = subscriptionRepository.findAll().firstOrNull()
    if (subscription == null) {
      val subscriptionResponse = hmctsSubscriptionApiClient.createSubscription(
        subscriptionRequest(),
        subscriptionCallbackConfig.subscriptionKey,
        xCorrelationId
      )
      subscriptionRepository.save(
        Subscription(
          id = subscriptionResponse.clientSubscriptionId,
        ),
      )
      secretsManagerService.setSecretValue(subscriptionResponse.hmac.secret)

      log.info("Subscription created")
    } else if (subscriptionCallbackConfig.updateSubscriptionOnStartup) {
      val subscriptionResponse = hmctsSubscriptionApiClient.updateSubscription(
        subscriptionRequest(),
        subscriptionCallbackConfig.subscriptionKey,
        subscription.id,
        xCorrelationId
      )
      subscription.updatedAt = LocalDateTime.now()

      log.info("Subscription updated")
    }
  }

  private fun subscriptionRequest() = SubscriptionRequest(
    notificationEndpoint = NotificationEndpoint(
      callbackUrl = subscriptionCallbackConfig.callbackUrl,
    ),
    eventTypes = subscriptionCallbackConfig.getEventTypesToSubscribe(),
  )

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
