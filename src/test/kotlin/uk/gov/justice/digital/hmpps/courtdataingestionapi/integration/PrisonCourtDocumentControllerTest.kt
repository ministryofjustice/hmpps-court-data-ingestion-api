package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.PrisonCourtDocumentDay
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.PrisonCourtDocumentWeek
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.PrisonRoll
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.PrisonRollService
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private const val READ_ROLE = "COURT_DATA_INGESTION__COURT_DATA_RO"
private const val PRISON = "LEI"
private const val HELD_HERE = "A1111AA"
private const val ALSO_HELD_HERE = "A2222AA"
private const val TRANSFERRED_OUT = "A3333AA"

class PrisonCourtDocumentControllerTest : IntegrationTestBase() {

  @Autowired
  private lateinit var jdbcTemplate: JdbcTemplate

  @MockitoBean
  private lateinit var rollService: PrisonRollService

  private val rollTakenAt = LocalDateTime.now().minusMinutes(3)
  private val thisMonday: LocalDate = LocalDate.now().with(DayOfWeek.MONDAY)

  private val testMonday: LocalDate = thisMonday.minusWeeks(1)

  @BeforeEach
  fun setUp() {
    courtDocumentRepository.deleteAll()

    whenever(rollService.rollFor(any()))
      .thenReturn(PrisonRoll(PRISON, listOf(HELD_HERE, ALSO_HELD_HERE), rollTakenAt))
    whenever(rollService.refresh(any())).then { }
  }

  @Test
  fun `the week is rejected without a token`() {
    webTestClient.get().uri(weekUri()).exchange().expectStatus().isUnauthorized
  }

  @Test
  fun `the week is rejected without a court data read role`() {
    webTestClient.get().uri(weekUri())
      .headers(setAuthorisation(roles = listOf("SOME_OTHER_ROLE")))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `the day is rejected without a court data read role`() {
    webTestClient.get().uri(dayUri())
      .headers(setAuthorisation(roles = listOf("SOME_OTHER_ROLE")))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `is allowed with the support role, which the admin screens use`() {
    webTestClient.get().uri(weekUri())
      .headers(setAuthorisation(roles = listOf("COURTCASE_RELEASEDATE_SUPPORT")))
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `the week returns a count for every day, including the ones with nothing`() {
    insertDocument(prisonerNumber = HELD_HERE, dayOfWeek = 1)

    val week = week()

    assertThat(week.days).hasSize(7)
    assertThat(week.days.map { it.date }).containsExactlyElementsOf((0..6).map { testMonday.plusDays(it.toLong()) })
  }

  @Test
  fun `a small week is listed in full, so a quiet prison never has to drill into a day`() {
    insertDocument(prisonerNumber = HELD_HERE, dayOfWeek = 1)
    insertDocument(prisonerNumber = ALSO_HELD_HERE, dayOfWeek = 3)

    val week = week()

    assertThat(week.rows).hasSize(2)
    assertThat(week.totalDocuments).isEqualTo(2)
  }

  @Test
  fun `a week beyond the threshold returns counts only, rather than a list nobody can read`() {
    repeat(101) { insertDocument(prisonerNumber = HELD_HERE, dayOfWeek = 1) }

    val week = week()

    assertThat(week.rows).isNull()
    assertThat(week.totalDocuments).isEqualTo(101)
    assertThat(week.rowThreshold).isEqualTo(100)
  }

  @Test
  fun `the week counts documents by day and reports distinct people`() {
    insertDocument(prisonerNumber = HELD_HERE, dayOfWeek = 1)
    insertDocument(prisonerNumber = HELD_HERE, dayOfWeek = 1)
    insertDocument(prisonerNumber = ALSO_HELD_HERE, dayOfWeek = 2)

    val days = week().days.associateBy { it.date }

    assertThat(days[testMonday.plusDays(1)]!!.documents).isEqualTo(2)
    assertThat(days[testMonday.plusDays(1)]!!.people).isEqualTo(1)
    assertThat(days[testMonday.plusDays(2)]!!.documents).isEqualTo(1)
  }

  @Test
  fun `returns documents for a person held here, whichever prison they were addressed to`() {
    insertDocument(prisonerNumber = HELD_HERE, addressedPrison = "MDI", dayOfWeek = 1)

    val document = day(testMonday.plusDays(1)).documentsWithoutAHearing.single()

    assertThat(document.prisonerNumber).isEqualTo(HELD_HERE)
    // The value of the view: paperwork that went elsewhere for someone we now hold.
    assertThat(document.addressedPrison).isEqualTo("MDI")
  }

  @Test
  fun `excludes a person who has left, even where the document was addressed to this prison`() {
    insertDocument(prisonerNumber = TRANSFERRED_OUT, dayOfWeek = 1)

    assertThat(day(testMonday.plusDays(1)).totalDocuments).isZero()
  }

  @Test
  fun `excludes unmatched documents, which belong on the national unmatched page`() {
    insertDocument(prisonerNumber = null, dayOfWeek = 1)

    assertThat(day(testMonday.plusDays(1)).totalDocuments).isZero()
  }

  @Test
  fun `the day holds only that day, with the whole of it included`() {
    insertDocument(prisonerNumber = HELD_HERE, dayOfWeek = 1, hour = 0)
    insertDocument(prisonerNumber = HELD_HERE, dayOfWeek = 1, hour = 23)
    insertDocument(prisonerNumber = HELD_HERE, dayOfWeek = 2)

    assertThat(day(testMonday.plusDays(1)).totalDocuments).isEqualTo(2)
  }

  @Test
  fun `the day returns the most recent arrival first`() {
    insertDocument(prisonerNumber = HELD_HERE, dayOfWeek = 1, hour = 9, courtHearingId = insertHearing())
    insertDocument(prisonerNumber = ALSO_HELD_HERE, dayOfWeek = 1, hour = 16, courtHearingId = insertHearing())

    assertThat(day(testMonday.plusDays(1)).hearings.first().prisonerNumber).isEqualTo(ALSO_HELD_HERE)
  }

  @Test
  fun `the day reports the people it covers once each, not once per document`() {
    insertDocument(prisonerNumber = HELD_HERE, dayOfWeek = 1)
    insertDocument(prisonerNumber = HELD_HERE, dayOfWeek = 1)
    insertDocument(prisonerNumber = ALSO_HELD_HERE, dayOfWeek = 1)

    val response = day(testMonday.plusDays(1))

    assertThat(response.totalDocuments).isEqualTo(3)
    assertThat(response.prisonerNumbers).containsExactlyInAnyOrder(HELD_HERE, ALSO_HELD_HERE)
  }

  @Test
  fun `documents on one hearing become one row, because they are one piece of work`() {
    val hearingId = insertHearing()
    insertDocument(prisonerNumber = HELD_HERE, dayOfWeek = 1, hour = 9, courtHearingId = hearingId)
    insertDocument(prisonerNumber = HELD_HERE, dayOfWeek = 1, hour = 14, courtHearingId = hearingId)

    val hearing = day(testMonday.plusDays(1)).hearings.single()

    assertThat(hearing.courtHearingId).isEqualTo(hearingId)
    assertThat(hearing.documents).hasSize(2)
    // The newest arrival is what put the hearing in the day.
    assertThat(hearing.receivedAt.hour).isEqualTo(14)
  }

  @Test
  fun `the hearing carries the court, the type and the date`() {
    insertDocument(prisonerNumber = HELD_HERE, dayOfWeek = 1, courtHearingId = insertHearing())

    val hearing = day(testMonday.plusDays(1)).hearings.single()

    assertThat(hearing.courtName).isEqualTo("Leeds Crown Court")
    assertThat(hearing.hearingType).isEqualTo("SENTENCE")
    assertThat(hearing.hearingDate).isEqualTo(testMonday.plusDays(1))
  }

  @Test
  fun `every case reference across a hearing is reported, since more than one is why it cannot be offered`() {
    val hearingId = insertHearing()
    val first = insertDocument(prisonerNumber = HELD_HERE, dayOfWeek = 1, courtHearingId = hearingId)
    val second = insertDocument(prisonerNumber = HELD_HERE, dayOfWeek = 1, courtHearingId = hearingId)
    insertCaseReference(first, "45AA1111111")
    insertCaseReference(second, "45AA2222222")

    assertThat(day(testMonday.plusDays(1)).hearings.single().caseReferences)
      .containsExactlyInAnyOrder("45AA1111111", "45AA2222222")
  }

  @Test
  fun `a document with no hearing link is kept apart, since it can never be offered`() {
    insertDocument(prisonerNumber = HELD_HERE, dayOfWeek = 1)

    val response = day(testMonday.plusDays(1))

    assertThat(response.hearings).isEmpty()
    assertThat(response.documentsWithoutAHearing).hasSize(1)
  }

  @Test
  fun `reports when the roll was taken, so the screen can say so rather than implying it is live`() {
    val week = week()

    assertThat(week.rollTakenAt).isEqualToIgnoringNanos(rollTakenAt)
    assertThat(week.rollSize).isEqualTo(2)
  }

  @Test
  fun `takes a fresh roll when asked, rather than using the cached one`() {
    webTestClient.get().uri("${dayUri()}&refreshRoll=true")
      .headers(setAuthorisation(roles = listOf(READ_ROLE)))
      .exchange()
      .expectStatus().isOk

    verify(rollService).refresh(PRISON)
  }

  @Test
  fun `accepts a lower case prison code`() {
    webTestClient.get()
      .uri("/court-document/prison/lei/week?date=${LocalDate.now()}")
      .headers(setAuthorisation(roles = listOf(READ_ROLE)))
      .exchange()
      .expectStatus().isOk

    verify(rollService).rollFor(PRISON)
  }

  @Test
  fun `an empty roll returns nothing rather than everything`() {
    insertDocument(prisonerNumber = HELD_HERE, dayOfWeek = 1)
    whenever(rollService.rollFor(any())).thenReturn(PrisonRoll(PRISON, emptyList(), rollTakenAt))

    val week = week()

    assertThat(week.totalDocuments).isZero()
    assertThat(week.rollSize).isZero()
  }

  private fun weekUri(date: LocalDate = testMonday) = "/court-document/prison/$PRISON/week?date=$date"

  private fun dayUri(date: LocalDate = testMonday.plusDays(1)) = "/court-document/prison/$PRISON/day?date=$date"

  private fun week(date: LocalDate = testMonday): PrisonCourtDocumentWeek = webTestClient.get()
    .uri(weekUri(date))
    .headers(setAuthorisation(roles = listOf(READ_ROLE)))
    .exchange()
    .expectStatus().isOk
    .expectBody<PrisonCourtDocumentWeek>()
    .returnResult()
    .responseBody!!

  private fun day(date: LocalDate): PrisonCourtDocumentDay = webTestClient.get().uri(dayUri(date))
    .headers(setAuthorisation(roles = listOf(READ_ROLE)))
    .exchange()
    .expectStatus().isOk
    .expectBody<PrisonCourtDocumentDay>()
    .returnResult()
    .responseBody!!

  private fun insertHearing(hearingDate: LocalDate = testMonday.plusDays(1)): UUID {
    val id = UUID.randomUUID()
    jdbcTemplate.update(
      """
      INSERT INTO court_hearing
        (id, hmcts_court_hearing_id, hmcts_court_id, court_name, hearing_type, hearing_date, created_at, updated_at)
      VALUES (?, ?, ?, 'Leeds Crown Court', 'SENTENCE', ?, now(), now())
      """.trimIndent(),
      id,
      UUID.randomUUID(),
      UUID.randomUUID(),
      hearingDate.atStartOfDay(),
    )
    return id
  }

  private fun insertDocument(
    prisonerNumber: String?,
    dayOfWeek: Int = 0,
    addressedPrison: String = PRISON,
    hour: Int = 9,
    courtHearingId: UUID? = null,
  ): UUID {
    val documentId = UUID.randomUUID()
    val receivedAt = testMonday.plusDays(dayOfWeek.toLong()).atTime(hour, 0)

    jdbcTemplate.update(
      """
      INSERT INTO court_document
        (id, master_defendant_id, hmcts_court_document_id, prison_document_id, prisoner_number,
         addressed_prison, prison_email_address, court_hearing_id, event_type,
         document_generated_timestamp, ingestion_at)
      VALUES (?, ?, ?, ?, ?, ?, 'omu.test@justice.gov.uk', ?, 'PRISON_COURT_REGISTER_GENERATED', ?, ?)
      """.trimIndent(),
      documentId,
      UUID.randomUUID(),
      UUID.randomUUID(),
      UUID.randomUUID(),
      prisonerNumber,
      addressedPrison,
      courtHearingId,
      receivedAt,
      receivedAt,
    )

    return documentId
  }

  private fun insertCaseReference(documentId: UUID, caseReference: String) = jdbcTemplate.update(
    "INSERT INTO court_document_case (id, court_document_id, case_reference) VALUES (?, ?, ?)",
    UUID.randomUUID(),
    documentId,
    caseReference,
  )

  @Test
  fun `any date in the week returns that whole week, Monday to Sunday`() {
    val week = week(testMonday.plusDays(3))

    assertThat(week.from).isEqualTo(testMonday)
    assertThat(week.to).isEqualTo(testMonday.plusDays(6))
  }

  @Test
  fun `a previous week can be viewed, and offers the weeks either side of it`() {
    val week = week(testMonday.plusDays(2))

    assertThat(week.from).isEqualTo(testMonday)
    assertThat(week.nextWeek).isEqualTo(thisMonday)
    assertThat(week.previousWeek).isEqualTo(testMonday.minusWeeks(1))
  }

  @Test
  fun `the current week offers no next week to step into`() {
    assertThat(week(LocalDate.now()).nextWeek).isNull()
  }

  @Test
  fun `a week that has not happened yet is refused, rather than returned empty`() {
    webTestClient.get().uri(weekUri(LocalDate.now().plusWeeks(1)))
      .headers(setAuthorisation(roles = listOf(READ_ROLE)))
      .exchange()
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.userMessage").value<String> { assertThat(it).contains("has not happened yet") }
  }

  @Test
  fun `a day that has not happened yet is refused`() {
    webTestClient.get().uri("/court-document/prison/$PRISON/day?date=${LocalDate.now().plusDays(1)}")
      .headers(setAuthorisation(roles = listOf(READ_ROLE)))
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `days still to come in the current week are present with nothing on them`() {
    val week = week(LocalDate.now())
    val future = week.days.filter { it.date.isAfter(LocalDate.now()) }

    assertThat(future).allMatch { it.documents == 0 && it.people == 0 }
  }
}
