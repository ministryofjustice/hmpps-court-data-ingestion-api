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

@Service
class SubscriptionService(
  private val subscriptionRepository: SubscriptionRepository,
  private val hmctsSubscriptionApiClient: HmctsSubscriptionApiClient,
  private val subscriptionCallbackConfig: SubscriptionCallbackConfig,
  private val secretsManagerService: SecretsManagerService,
) {

  @Transactional
  fun subscribe() {
    val subscription = subscriptionRepository.findAll().firstOrNull()
    if (subscription == null) {
      val subscriptionResponse = hmctsSubscriptionApiClient.createSubscription(
        subscriptionRequest(),
        subscriptionCallbackConfig.subscriptionKey,
      )
      subscriptionRepository.save(
        Subscription(
          id = subscriptionResponse.clientSubscriptionId,
        ),
      )
      secretsManagerService.setSecretValue(subscriptionResponse.hmac.secret)

      log.info("Subscription created")
    } else {
      val subscriptionResponse = hmctsSubscriptionApiClient.updateSubscription(
        subscriptionRequest(),
        subscriptionCallbackConfig.subscriptionKey,
        subscription.id,
      )
      subscription.updatedAt = LocalDateTime.now()
      secretsManagerService.setSecretValue(subscriptionResponse.hmac.secret)

      log.info("Subscription updated")
    }
  }

  fun subscriptionRequest() = SubscriptionRequest(
    notificationEndpoint = NotificationEndpoint(
      callbackUrl = subscriptionCallbackConfig.callbackUrl,
    ),
    eventTypes = listOf(
      "PRISON_COURT_REGISTER_GENERATED",
    ),
  )

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
