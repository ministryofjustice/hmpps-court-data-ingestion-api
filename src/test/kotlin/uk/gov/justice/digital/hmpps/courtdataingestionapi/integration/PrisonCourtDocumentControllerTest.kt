package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsSubcriptionApiExtension.Companion.hmctsSubcriptionApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.PrisonerSearchApiExtension.Companion.prisonerSearchApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.PrisonCourtDocumentDay
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.PrisonCourtDocumentWeek
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

class PrisonCourtDocumentControllerTest : IntegrationTestBase() {

  @Test
  fun `requires a token`() {
    webTestClient.get().uri(weekUri()).exchange().expectStatus().isUnauthorized
  }

  @Test
  fun `accepts the court data read write role, which the frontend client holds`() {
    prisonerSearchApi.stubPrisonersInPrison(PRISON, MATCHING_PRISONER_NUMBER)

    webTestClient.get().uri(weekUri())
      .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RW")))
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `requires the court data read role`() {
    webTestClient.get().uri(weekUri())
      .headers(setAuthorisation(roles = listOf("SOME_OTHER_ROLE")))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `returns documents for a person on the roll`() {
    prisonerSearchApi.stubPrisonersInPrison(PRISON, MATCHING_PRISONER_NUMBER)
    sendSubscriptionNotificationWaitForRecordToBeCreated(MATCHING_CORE_PERSON)

    val week = week()

    assertThat(week.totalDocuments).isEqualTo(1)
    assertThat(week.documents!!.single().prisonerNumber).isEqualTo(MATCHING_PRISONER_NUMBER)
    assertThat(week.days.single { it.date == LocalDate.now() }.documents).isEqualTo(1)
  }

  @Test
  fun `excludes a person who is not on the roll`() {
    prisonerSearchApi.stubPrisonersInPrison(PRISON, "Z9999ZZ")
    sendSubscriptionNotificationWaitForRecordToBeCreated(MATCHING_CORE_PERSON)

    assertThat(week().totalDocuments).isZero()
  }

  @Test
  fun `an empty roll returns nothing`() {
    prisonerSearchApi.stubPrisonersInPrison(PRISON)
    sendSubscriptionNotificationWaitForRecordToBeCreated(MATCHING_CORE_PERSON)

    val week = week()

    assertThat(week.totalDocuments).isZero()
    assertThat(week.rollSize).isZero()
  }

  @Test
  fun `the week always has seven days, Monday to Sunday`() {
    prisonerSearchApi.stubPrisonersInPrison(PRISON, MATCHING_PRISONER_NUMBER)

    val week = week()

    assertThat(week.days).hasSize(7)
    assertThat(week.from).isEqualTo(LocalDate.now().with(DayOfWeek.MONDAY))
    assertThat(week.nextWeek).isNull()
  }

  @Test
  fun `a future week is refused`() {
    webTestClient.get().uri(weekUri(LocalDate.now().plusWeeks(1)))
      .headers(setAuthorisation(roles = listOf(READ_ROLE)))
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `the day is rejected without the court data read role`() {
    webTestClient.get().uri(dayUri())
      .headers(setAuthorisation(roles = listOf("SOME_OTHER_ROLE")))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `the day groups documents under their hearing`() {
    prisonerSearchApi.stubPrisonersInPrison(PRISON, MATCHING_PRISONER_NUMBER)
    sendSubscriptionNotificationWaitForRecordToBeCreated(MATCHING_CORE_PERSON)

    val hearing = day().hearings.single()

    assertThat(hearing.prisonerNumber).isEqualTo(MATCHING_PRISONER_NUMBER)
    assertThat(hearing.hearingType).isEqualTo("First hearing")
    assertThat(hearing.courtName).isEqualTo("Central London County Court")
    assertThat(hearing.caseReferences).containsExactly(CASE_REFERENCE)
  }

  @Test
  fun `two documents on one hearing are one hearing`() {
    prisonerSearchApi.stubPrisonersInPrison(PRISON, MATCHING_PRISONER_NUMBER)
    val first = UUID.randomUUID().also { hmctsSubcriptionApi.stubFile(it) }
    val second = UUID.randomUUID().also { hmctsSubcriptionApi.stubFile(it) }
    sendSubscriptionNotification(MATCHING_CORE_PERSON, documentId = first)
    sendSubscriptionNotification(MATCHING_CORE_PERSON, documentId = second)
    awaitAtMost30Secs untilCallTo {
      courtDocumentRepository.countByMasterDefendantId(MATCHING_CORE_PERSON)
    } matches { it == 2L }

    val day = day()

    assertThat(day.hearings.single().documents).hasSize(2)
    assertThat(day.people.map { it.prisonerNumber }).containsExactly(MATCHING_PRISONER_NUMBER)
  }

  @Test
  fun `excludes documents not matched to a person`() {
    prisonerSearchApi.stubPrisonersInPrison(PRISON, MATCHING_PRISONER_NUMBER)
    sendSubscriptionNotificationWaitForRecordToBeCreated(NOT_FOUND_CORE_PERSON)

    assertThat(week().totalDocuments).isZero()
  }

  @Test
  fun `a document received today is not in the previous week`() {
    prisonerSearchApi.stubPrisonersInPrison(PRISON, MATCHING_PRISONER_NUMBER)
    sendSubscriptionNotificationWaitForRecordToBeCreated(MATCHING_CORE_PERSON)
    val thisMonday = LocalDate.now().with(DayOfWeek.MONDAY)

    val lastWeek = week(thisMonday.minusDays(1))

    assertThat(lastWeek.totalDocuments).isZero()
    assertThat(lastWeek.from).isEqualTo(thisMonday.minusWeeks(1))
    assertThat(lastWeek.nextWeek).isEqualTo(thisMonday)
  }

  @Test
  fun `a future day is refused`() {
    webTestClient.get().uri(dayUri(LocalDate.now().plusDays(1)))
      .headers(setAuthorisation(roles = listOf(READ_ROLE)))
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `accepts a lower case prison code`() {
    prisonerSearchApi.stubPrisonersInPrison(PRISON, MATCHING_PRISONER_NUMBER)
    sendSubscriptionNotificationWaitForRecordToBeCreated(MATCHING_CORE_PERSON)

    webTestClient.get().uri("/court-document/prison/lei/week?date=${LocalDate.now()}")
      .headers(setAuthorisation(roles = listOf(READ_ROLE)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.totalDocuments").isEqualTo(1)
  }

  @Test
  fun `asks prisoner search to leave out restricted patients`() {
    prisonerSearchApi.stubPrisonersInPrison(PRISON, MATCHING_PRISONER_NUMBER)

    week()

    prisonerSearchApi.verify(
      getRequestedFor(urlPathEqualTo("/prisoner-search/prison/$PRISON"))
        .withQueryParam("include-restricted-patients", equalTo("false")),
    )
  }

  @Test
  fun `sends a JSON content type, which prisoner search requires even on a GET`() {
    prisonerSearchApi.stubPrisonersInPrison(PRISON, MATCHING_PRISONER_NUMBER)

    week()

    prisonerSearchApi.verify(
      getRequestedFor(urlPathEqualTo("/prisoner-search/prison/$PRISON"))
        .withHeader("Content-Type", equalTo("application/json")),
    )
  }

  private fun weekUri(date: LocalDate = LocalDate.now()) = "/court-document/prison/$PRISON/week?date=$date"

  private fun dayUri(date: LocalDate = LocalDate.now()) = "/court-document/prison/$PRISON/day?date=$date"

  private fun week(date: LocalDate = LocalDate.now()): PrisonCourtDocumentWeek = webTestClient.get().uri(weekUri(date))
    .headers(setAuthorisation(roles = listOf(READ_ROLE)))
    .exchange()
    .expectStatus().isOk
    .expectBody<PrisonCourtDocumentWeek>()
    .returnResult().responseBody!!

  private fun day(): PrisonCourtDocumentDay = webTestClient.get()
    .uri(dayUri())
    .headers(setAuthorisation(roles = listOf(READ_ROLE)))
    .exchange()
    .expectStatus().isOk
    .expectBody<PrisonCourtDocumentDay>()
    .returnResult().responseBody!!

  private companion object {
    const val PRISON = "LEI"
    const val READ_ROLE = "COURT_DATA_INGESTION__COURT_DATA_RO"
  }
}
