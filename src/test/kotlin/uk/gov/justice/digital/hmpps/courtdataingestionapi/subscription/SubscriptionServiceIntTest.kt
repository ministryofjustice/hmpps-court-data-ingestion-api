package uk.gov.justice.digital.hmpps.courtdataingestionapi.subscription

import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsApiMockServer
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.SubscriptionRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.SecretsManagerService
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.SubscriptionService

class SubscriptionServiceIntTest : IntegrationTestBase() {

  @Autowired
  lateinit var subscriptionService: SubscriptionService

  @Autowired
  lateinit var subscriptionRepository: SubscriptionRepository

  @Autowired
  lateinit var secretsManagerService: SecretsManagerService

  @Test
  fun `should create subscription on startup if none exists`() {
    val subscriptions = subscriptionRepository.findAll()

    assertThat(subscriptions).hasSize(1)
    assertThat(subscriptions.first().id).isEqualTo(HmctsApiMockServer.TEST_SUBSCRIPTION_ID)
  }

  @Test
  fun `should update subscription on startup if one exists`() {
    val subscription = subscriptionRepository.findAll()[0]
    val created = subscription.subscribedAt

    subscriptionService.subscribe()

    val subscriptions = subscriptionRepository.findAll()

    assertThat(subscriptions).hasSize(1)
    assertThat(subscriptions.first().id).isEqualTo(HmctsApiMockServer.TEST_SUBSCRIPTION_ID)
    assertThat(subscriptions.first().updatedAt).isAfter(created)
    HmctsApiExtension.hmctsApi.verify(putRequestedFor(urlPathEqualTo("/hrds/client-subscriptions/${subscription.id}")))

    val secret = secretsManagerService.getSecretValue()
    assertThat(secret).isEqualTo(HmctsApiMockServer.TEST_HMAC_KEY)
  }
}
