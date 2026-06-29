package uk.gov.justice.digital.hmpps.courtdataingestionapi.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.returnResult
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.ThingsToDo
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.ToDoType

@Transactional
class ThingsToDoControllerIntTest : IntegrationTestBase() {

  @Test
  fun `Person with document thing to do`() {
    sendSubscriptionNotification(MATCHING_CORE_PERSON)

    val result = webTestClient
      .get()
      .uri("/things-to-do/prisoner/${MATCHING_PRISONER_NUMBER}")
      .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RO")))
      .exchange()
      .expectStatus()
      .isOk
      .returnResult(ThingsToDo::class.java)
      .responseBody
      .blockFirst()!!

    assertThat(result.thingsToDo.size).isGreaterThanOrEqualTo(1)
    assertThat(result.thingsToDo[0]).isEqualTo(ToDoType.HMCTS_API_DOCUMENT_RECEIVED)
  }

  @Test
  fun `Person with no things to do`() {
    val result = webTestClient
      .get()
      .uri("/things-to-do/prisoner/XYZ098")
      .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RO")))
      .exchange()
      .expectStatus()
      .isOk
      .returnResult<ThingsToDo>()
      .responseBody
      .blockFirst()!!

    assertThat(result.thingsToDo).hasSize(0)
  }
}
