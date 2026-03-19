package uk.gov.justice.digital.hmpps.courtdataingestionapi.subscription

import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
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

  //  @Test TODO comment out while testing secrets manager integration
//  fun `should create subscribe on startup if none exists`() {
//    val subscriptions = subscriptionRepository.findAll()
//
//    assertThat(subscriptions).hasSize(1)
//    assertThat(subscriptions.first().id).isEqualTo(HmctsSubscriptionApiMockServer.TEST_SUBSCRIPTION_ID)
//  }
//
// //  @Test TODO comment out while testing secrets manager integration
//  fun `should update subscribe on startup if one exists`() {
//    val subscription = subscriptionRepository.findAll()[0]
//    val created = subscription.subscribedAt
//
//    subscriptionService.subscribe()
//
//    val subscriptions = subscriptionRepository.findAll()
//
//    assertThat(subscriptions).hasSize(1)
//    assertThat(subscriptions.first().id).isEqualTo(HmctsSubscriptionApiMockServer.TEST_SUBSCRIPTION_ID)
//    assertThat(subscriptions.first().updatedAt).isAfter(created)
//    HmctsSubscriptionApiExtension.hmctsSubscriptionApi.verify(putRequestedFor(urlPathEqualTo("/client-subscriptions/${subscription.id}")))
//
//    val secret = secretsManagerService.getSecretValue()
//    assertThat(secret).isEqualTo(HmctsSubscriptionApiMockServer.TEST_HMAC_KEY)
//  }
}
