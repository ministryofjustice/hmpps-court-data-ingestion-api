package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsCourtScheduleApiExtension.Companion.hmctsCourtScheduleApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsSubcriptionApiMockServer.Companion.TEST_HMCTS_COURTHOUSE_ID
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsSubcriptionApiMockServer.Companion.TEST_HMCTS_HEARING_ID
import uk.gov.justice.digital.hmpps.courtdataingestionapi.listener.HmctsCase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtScheduleSnapshotRepository
import java.time.LocalDate

private const val SNAPSHOT_PRISON = "HLI"
private const val SNAPSHOT_CASE = "SNAP000001"

@TestPropertySource(properties = ["court-schedule-snapshot.prisons=$SNAPSHOT_PRISON"])
class CourtScheduleSnapshotBackfillIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var jdbcTemplate: JdbcTemplate

  @Autowired
  private lateinit var snapshotRepository: CourtScheduleSnapshotRepository

  @BeforeEach
  fun setUp() = clearData()

  @AfterEach
  fun tearDown() = clearData()

  private fun clearData() {
    snapshotRepository.deleteAll()
    courtDocumentRepository.deleteAll()
    jdbcTemplate.update("DELETE FROM prison_email_mapping WHERE email = ?", PRISON_EMAIL)
  }

  @Test
  fun `the first fetch for a case stores the body`() {
    hmctsCourtScheduleApi.stubCourtScheduleResponse(SNAPSHOT_CASE, 200, scheduleBody("2026-11-02T10:00:00Z"))
    ingestDocumentAddressedTo(SNAPSHOT_PRISON)

    runBackfill("court-schedule-snapshot")

    val snapshots = snapshotRepository.findByCaseReferenceOrderByFetchedAt(SNAPSHOT_CASE)
    assertThat(snapshots).hasSize(1)
    assertThat(snapshots[0].httpStatus).isEqualTo(200)
    assertThat(snapshots[0].changed).isTrue()
    assertThat(snapshots[0].body).contains("2026-11-02T10:00:00Z")
  }

  @Test
  fun `an unchanged schedule records the fetch without storing the body again`() {
    hmctsCourtScheduleApi.stubCourtScheduleResponse(SNAPSHOT_CASE, 200, scheduleBody("2026-11-02T10:00:00Z"))
    ingestDocumentAddressedTo(SNAPSHOT_PRISON)

    runBackfill("court-schedule-snapshot")
    snapshotsFetchedYesterday()
    runBackfill("court-schedule-snapshot")

    val snapshots = snapshotRepository.findByCaseReferenceOrderByFetchedAt(SNAPSHOT_CASE)
    assertThat(snapshots).hasSize(2)
    assertThat(snapshots[1].changed).isFalse()
    assertThat(snapshots[1].body).isNull()
    assertThat(snapshots[1].bodySha256).isEqualTo(snapshots[0].bodySha256)
  }

  @Test
  fun `a moved hearing stores the new body`() {
    hmctsCourtScheduleApi.stubCourtScheduleResponse(SNAPSHOT_CASE, 200, scheduleBody("2026-11-02T10:00:00Z"))
    ingestDocumentAddressedTo(SNAPSHOT_PRISON)
    runBackfill("court-schedule-snapshot")

    snapshotsFetchedYesterday()
    hmctsCourtScheduleApi.stubCourtScheduleResponse(SNAPSHOT_CASE, 200, scheduleBody("2026-11-09T10:00:00Z"))
    runBackfill("court-schedule-snapshot")

    val snapshots = snapshotRepository.findByCaseReferenceOrderByFetchedAt(SNAPSHOT_CASE)
    assertThat(snapshots).hasSize(2)
    assertThat(snapshots[1].changed).isTrue()
    assertThat(snapshots[1].body).contains("2026-11-09T10:00:00Z")
  }

  @Test
  fun `a second run on the same day skips cases already fetched`() {
    hmctsCourtScheduleApi.stubCourtScheduleResponse(SNAPSHOT_CASE, 200, scheduleBody("2026-11-02T10:00:00Z"))
    ingestDocumentAddressedTo(SNAPSHOT_PRISON)

    runBackfill("court-schedule-snapshot")
    runBackfill("court-schedule-snapshot")

    assertThat(snapshotRepository.findByCaseReferenceOrderByFetchedAt(SNAPSHOT_CASE)).hasSize(1)
  }

  @Test
  fun `an error response is recorded with its status`() {
    hmctsCourtScheduleApi.stubCourtScheduleResponse(SNAPSHOT_CASE, 404, """{"error":"NOT_FOUND"}""")
    ingestDocumentAddressedTo(SNAPSHOT_PRISON)

    runBackfill("court-schedule-snapshot")

    val snapshots = snapshotRepository.findByCaseReferenceOrderByFetchedAt(SNAPSHOT_CASE)
    assertThat(snapshots).hasSize(1)
    assertThat(snapshots[0].httpStatus).isEqualTo(404)
    assertThat(snapshots[0].body).isNull()
    assertThat(snapshots[0].errorBody).contains("NOT_FOUND")
    assertThat(snapshots[0].changed).isFalse()
  }

  @Test
  fun `cases addressed to other prisons are not fetched`() {
    hmctsCourtScheduleApi.stubCourtScheduleResponse(SNAPSHOT_CASE, 200, scheduleBody("2026-11-02T10:00:00Z"))
    ingestDocumentAddressedTo("LEI")

    runBackfill("court-schedule-snapshot")

    assertThat(snapshotRepository.findByCaseReferenceOrderByFetchedAt(SNAPSHOT_CASE)).isEmpty()
  }

  @Test
  fun `a case with an old document stays in while its schedule has a hearing to come`() {
    hmctsCourtScheduleApi.stubCourtScheduleResponse(SNAPSHOT_CASE, 200, scheduleBody(sittingInDays(30)))
    ingestDocumentAddressedTo(SNAPSHOT_PRISON)
    runBackfill("court-schedule-snapshot")

    documentsIngestedDaysAgo(60)
    snapshotsFetchedYesterday()
    runBackfill("court-schedule-snapshot")

    assertThat(snapshotRepository.findByCaseReferenceOrderByFetchedAt(SNAPSHOT_CASE)).hasSize(2)
  }

  @Test
  fun `a case with an old document leaves once its hearings are past the grace period`() {
    hmctsCourtScheduleApi.stubCourtScheduleResponse(SNAPSHOT_CASE, 200, scheduleBody(sittingInDays(-30)))
    ingestDocumentAddressedTo(SNAPSHOT_PRISON)
    runBackfill("court-schedule-snapshot")

    documentsIngestedDaysAgo(60)
    snapshotsFetchedYesterday()
    runBackfill("court-schedule-snapshot")

    assertThat(snapshotRepository.findByCaseReferenceOrderByFetchedAt(SNAPSHOT_CASE)).hasSize(1)
  }

  @Test
  fun `a week commencing listing keeps a case in`() {
    hmctsCourtScheduleApi.stubCourtScheduleResponse(SNAPSHOT_CASE, 200, weekCommencingBody(LocalDate.now().plusDays(21)))
    ingestDocumentAddressedTo(SNAPSHOT_PRISON)
    runBackfill("court-schedule-snapshot")

    documentsIngestedDaysAgo(60)
    snapshotsFetchedYesterday()
    runBackfill("court-schedule-snapshot")

    assertThat(snapshotRepository.findByCaseReferenceOrderByFetchedAt(SNAPSHOT_CASE)).hasSize(2)
  }

  private fun ingestDocumentAddressedTo(prisonCode: String) {
    jdbcTemplate.update(
      "INSERT INTO prison_email_mapping (email, prison_code, category_code) VALUES (?, ?, 'PRISON')",
      PRISON_EMAIL,
      prisonCode,
    )
    sendSubscriptionNotificationWaitForRecordToBeCreated(MATCHING_CORE_PERSON, hmctsCases = listOf(HmctsCase(SNAPSHOT_CASE)))

    awaitAtMost30Secs untilCallTo {
      courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(MATCHING_CORE_PERSON)
    } matches { it != null && it.prisonerNumber == MATCHING_PRISONER_NUMBER && it.addressedPrison == prisonCode }
  }

  private fun snapshotsFetchedYesterday() {
    jdbcTemplate.update("UPDATE court_schedule_snapshot SET fetched_at = fetched_at - INTERVAL '1 day'")
  }

  private fun documentsIngestedDaysAgo(days: Long) {
    jdbcTemplate.update("UPDATE court_document SET ingestion_at = now() - make_interval(days => ?)", days.toInt())
  }

  private fun sittingInDays(days: Long) = "${LocalDate.now().plusDays(days)}T10:00:00Z"

  private fun weekCommencingBody(startDate: LocalDate) = """
    {
      "courtSchedule": [
        {
          "hearings": [
            {
              "hearingId": "$TEST_HMCTS_HEARING_ID",
              "hearingType": "Trial",
              "hearingDescription": "Trial",
              "listNote": null,
              "courtSittings": [],
              "weekCommencing": {
                "startDate": "$startDate",
                "endDate": "${startDate.plusDays(6)}",
                "durationInWeeks": 1,
                "courtHouse": "$TEST_HMCTS_COURTHOUSE_ID"
              }
            }
          ]
        }
      ]
    }
  """.trimIndent()

  private fun scheduleBody(sittingStart: String) = """
    {
      "courtSchedule": [
        {
          "hearings": [
            {
              "hearingId": "$TEST_HMCTS_HEARING_ID",
              "hearingType": "Trial",
              "hearingDescription": "Trial",
              "listNote": null,
              "courtSittings": [
                {
                  "courtHouse": "$TEST_HMCTS_COURTHOUSE_ID",
                  "courtRoom": "b4562684-9209-3ec4-a544-7f80dabd94d8",
                  "judiciaryId": "",
                  "sittingStart": "$sittingStart",
                  "sittingEnd": "$sittingStart"
                }
              ]
            }
          ]
        }
      ]
    }
  """.trimIndent()
}
