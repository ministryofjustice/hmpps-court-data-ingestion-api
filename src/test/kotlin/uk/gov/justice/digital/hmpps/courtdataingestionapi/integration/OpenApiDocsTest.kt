package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration

import io.swagger.v3.core.util.Json31
import io.swagger.v3.oas.models.OpenAPI
import net.minidev.json.JSONArray
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class OpenApiDocsTest : IntegrationTestBase() {
  @Test
  fun `open api docs are available`() {
    webTestClient.get()
      .uri("/swagger-ui/index.html?configUrl=/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `open api docs redirect to correct page`() {
    webTestClient.get()
      .uri("/swagger-ui.html")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().is3xxRedirection
      .expectHeader().value("Location") { it.contains("/swagger-ui/index.html?configUrl=/v3/api-docs/swagger-config") }
  }

  @Test
  fun `the open api json contains documentation`() {
    val openApi = readOpenApi()
    assertThat(openApi.paths).isNotEmpty
  }

  @Test
  fun `the open api json contains the version number`() {
    val openApi = readOpenApi()
    assertThat(openApi.info.version).isNotBlank()
    assertThat(openApi.info.version).startsWith(DateTimeFormatter.ISO_DATE.format(LocalDate.now()))
  }

  @Test
  fun `the open api json is valid`() {
    val openApi = readOpenApi()
    assertThat(openApi.info.title).isNotBlank()
    assertThat(openApi.info.version).isNotBlank()
  }

  @Test
  @Disabled("TODO Enable this test once you have added security schema to OpenApiConfiguration.OpenAPi().components()")
  fun `the open api json path security requirements are valid`() {
    val openApi = readOpenApi()

    // The security requirements of each path don't appear to be validated like they are at https://editor.swagger.io/
    // We therefore need to grab all the valid security requirements and check that each path only contains those items
    val securityRequirements = openApi.security.flatMap { it.keys }
    openApi.paths.forEach { pathItem ->
      assertThat(pathItem.value.get.security.flatMap { it.keys }).isSubsetOf(securityRequirements)
    }
  }

  @ParameterizedTest
  @Disabled("TODO Enable this test once you have added security schema to OpenApiConfiguration.OpenAPi().components(). Add the security scheme / roles to @CsvSource")
  @CsvSource(value = ["security-scheme-name, ROLE_"])
  fun `the security scheme is setup for bearer tokens`(key: String, role: String) {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.components.securitySchemes.$key.type").isEqualTo("http")
      .jsonPath("$.components.securitySchemes.$key.scheme").isEqualTo("bearer")
      .jsonPath("$.components.securitySchemes.$key.description").value<String> {
        assertThat(it).contains(role)
      }
      .jsonPath("$.components.securitySchemes.$key.bearerFormat").isEqualTo("JWT")
      .jsonPath("$.security[0].$key").isEqualTo(JSONArray().apply { this.add("read") })
  }

  @Test
  @Disabled("TODO Enable this test once you have an endpoint.")
  fun `all endpoints have a security scheme defined`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.paths[*][*][?(!@.security)]").doesNotExist()
  }

  private fun readOpenApi(): OpenAPI {
    val body = webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody<String>()
      .returnResult().responseBody!!
    return Json31.mapper().readValue(body, OpenAPI::class.java)
  }
}
