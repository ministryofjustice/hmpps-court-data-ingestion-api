package uk.gov.justice.digital.hmpps.courtdataingestionapi.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest
import software.amazon.awssdk.services.secretsmanager.model.DescribeSecretRequest
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException
import uk.gov.justice.hmpps.sqs.Provider
import uk.gov.justice.hmpps.sqs.findProvider

@Configuration
@EnableConfigurationProperties(HmppsSecretManagerProperties::class)
class HmppsSecretsManagerConfig(
  private val hmppsSecretManagerProperties: HmppsSecretManagerProperties,
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Bean
  fun secretsManagerClient(): SecretsManagerClient = with(hmppsSecretManagerProperties) {
    when (findProvider(provider)) {
      Provider.AWS -> awsSecretsManagerClient()
      Provider.LOCALSTACK -> localstackSecretsClient()
    }
  }

  private fun awsSecretsManagerClient() = with(hmppsSecretManagerProperties) {
    log.info("Creating AWS SecretsManagerClient with DefaultCredentialsProvider and region '$region'")

    SecretsManagerClient.builder()
      .credentialsProvider(DefaultCredentialsProvider.builder().build())
      .region(Region.of(region))
      .build()
  }

  private fun localstackSecretsClient(): SecretsManagerClient = with(hmppsSecretManagerProperties) {
    log.info("Creating localstack SecretsManagerClient with StaticCredentialsProvider, localstackUrl '$localstackUrl' and region '$region'")

    val client = SecretsManagerClient.builder()
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("any", "any")))
      .endpointOverride(java.net.URI.create(localstackUrl.replace("localhost", "127.0.0.1")))
      .region(Region.of(region))
      .build()

    log.info("Checking for Secret '$secretId'")

    try {
      val describeSecretRequest = DescribeSecretRequest.builder()
        .secretId(secretId)
        .build()
      client.describeSecret(describeSecretRequest)
      log.info("Secret  '$secretId' found")
    } catch (e: ResourceNotFoundException) {
      log.info("Creating Secret '$secretId' as it was not found")

      val bucketRequest = CreateSecretRequest.builder()
        .name(secretId)
        .build()

      client.createSecret(bucketRequest)
    }

    return client
  }
}
