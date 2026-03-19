package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueRequest
import uk.gov.justice.digital.hmpps.courtdataingestionapi.config.HmppsSecretManagerProperties

@Service
class SecretsManagerService(private val secretsManagerClient: SecretsManagerClient, private val properties: HmppsSecretManagerProperties) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  /**
   * Returns the value of the specified secret, or an empty string if no value is found.
   */
  fun getSecretValue(): String {
    log.info("Retrieving secret value ${properties.secretId}")
    val getSecretValueRequest = GetSecretValueRequest.builder()
      .secretId(properties.secretId)
      .build()

    val secret = secretsManagerClient.getSecretValue(getSecretValueRequest)
    if (secret == null) {
      return ""
    }
    return secret.secretString().orEmpty()
  }

  fun setSecretValue(secretValue: String) {
    log.info("Setting secret value ${properties.secretId}")
    val putSecretValueRequest = PutSecretValueRequest.builder()
      .secretId(properties.secretId)
      .secretString(secretValue)
      .build()

    val response = secretsManagerClient.putSecretValue(putSecretValueRequest)

    log.info("Secret version created ${response.versionId()} for secret ${properties.secretId}")
  }
}
