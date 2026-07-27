package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill.BackfillRunner
import uk.gov.justice.digital.hmpps.courtdataingestionapi.controller.BackfillAdminController
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.BackfillRun
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.BackfillRunStatus
import java.util.UUID

private const val SUPPORT_ROLE = "COURTCASE_RELEASEDATE_SUPPORT"
private const val KNOWN_BACKFILL = "extraction"
private const val UNKNOWN_BACKFILL = "not-a-real-backfill"

class BackfillAdminControllerTest : IntegrationTestBase() {

  @MockitoBean
  private lateinit var runner: BackfillRunner

  @BeforeEach
  fun stubRunner() {
    whenever(runner.acquireLock(any(), any())).thenAnswer { invocation ->
      BackfillRun(
        backfillId = invocation.getArgument(0),
        status = BackfillRunStatus.RUNNING,
        triggeredBy = invocation.getArgument(1),
      )
    }
  }

  @Test
  fun `listing backfills is rejected without a token`() {
    webTestClient.get().uri("/admin/backfill")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `listing backfills is rejected without the support role`() {
    webTestClient.get().uri("/admin/backfill")
      .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RO")))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `listing backfills returns every registered id in order`() {
    val response = webTestClient.get().uri("/admin/backfill")
      .headers(setAuthorisation(roles = listOf(SUPPORT_ROLE)))
      .exchange()
      .expectStatus().isOk
      .expectBody<BackfillAdminController.BackfillListResponse>()
      .returnResult().responseBody!!

    assertThat(response.registered).contains(KNOWN_BACKFILL, "hash", "mirror")
    assertThat(response.registered).isSorted()
  }

  @Test
  fun `retrieving an unregistered backfill returns not found`() {
    webTestClient.get().uri("/admin/backfill/$UNKNOWN_BACKFILL")
      .headers(setAuthorisation(roles = listOf(SUPPORT_ROLE)))
      .exchange()
      .expectStatus().isNotFound
  }

  @Test
  fun `retrieving a registered backfill reports its most recent run`() {
    val response = webTestClient.get().uri("/admin/backfill/$KNOWN_BACKFILL")
      .headers(setAuthorisation(roles = listOf(SUPPORT_ROLE)))
      .exchange()
      .expectStatus().isOk
      .expectBody<BackfillAdminController.BackfillRunResponse>()
      .returnResult().responseBody!!

    assertThat(response.backfillId).isEqualTo(KNOWN_BACKFILL)
    assertThat(response.status).isIn(
      BackfillAdminController.NO_RUNS,
      BackfillRunStatus.RUNNING.name,
      BackfillRunStatus.COMPLETED.name,
      BackfillRunStatus.FAILED.name,
    )
  }

  @Test
  fun `starting a backfill is rejected without the support role`() {
    webTestClient.post().uri("/admin/backfill/$KNOWN_BACKFILL")
      .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RW")))
      .exchange()
      .expectStatus().isForbidden

    verify(runner, never()).acquireLock(any(), any())
  }

  @Test
  fun `starting a backfill accepts the request and records the caller`() {
    val response = webTestClient.post().uri("/admin/backfill/$KNOWN_BACKFILL")
      .headers(setAuthorisation(username = "JOEL_GEN", roles = listOf(SUPPORT_ROLE)))
      .exchange()
      .expectStatus().isAccepted
      .expectBody<BackfillAdminController.TriggerResponse>()
      .returnResult().responseBody!!

    assertThat(response.runId).isNotNull()
    assertThat(response.message).contains(KNOWN_BACKFILL)

    verify(runner).acquireLock(eq(KNOWN_BACKFILL), eq("JOEL_GEN"))
    verify(runner).runAsync(any(), any())
  }

  @Test
  fun `starting a backfill that is already in flight returns conflict and does not start a second run`() {
    whenever(runner.acquireLock(eq(KNOWN_BACKFILL), any())).thenReturn(null)

    val response = webTestClient.post().uri("/admin/backfill/$KNOWN_BACKFILL")
      .headers(setAuthorisation(roles = listOf(SUPPORT_ROLE)))
      .exchange()
      .expectStatus().isEqualTo(409)
      .expectBody<BackfillAdminController.TriggerResponse>()
      .returnResult().responseBody!!

    assertThat(response.runId).isNull()
    verify(runner, never()).runAsync(any(), any())
  }

  @Test
  fun `starting an unregistered backfill returns not found and never takes the lock`() {
    webTestClient.post().uri("/admin/backfill/$UNKNOWN_BACKFILL")
      .headers(setAuthorisation(roles = listOf(SUPPORT_ROLE)))
      .exchange()
      .expectStatus().isNotFound

    verify(runner, never()).acquireLock(any(), any())
    verify(runner, never()).runAsync(any(), any())
  }

  @Test
  fun `the run id returned is the one handed to the async runner`() {
    val fixedRunId = UUID.randomUUID()
    whenever(runner.acquireLock(eq(KNOWN_BACKFILL), any())).thenReturn(
      BackfillRun(runId = fixedRunId, backfillId = KNOWN_BACKFILL, status = BackfillRunStatus.RUNNING),
    )

    val response = webTestClient.post().uri("/admin/backfill/$KNOWN_BACKFILL")
      .headers(setAuthorisation(roles = listOf(SUPPORT_ROLE)))
      .exchange()
      .expectStatus().isAccepted
      .expectBody<BackfillAdminController.TriggerResponse>()
      .returnResult().responseBody!!

    assertThat(response.runId).isEqualTo(fixedRunId)
    verify(runner).runAsync(eq(fixedRunId), any())
  }
}
