package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.BackfillRun
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.BackfillRunStatus
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.BackfillRunRepository
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BackfillRunnerTest {

  private val repository: BackfillRunRepository = mock()

  private val store = BackfillRunStore(repository)
  private val runner = BackfillRunner(
    repository,
    store,
    staleThresholdIso = "PT5M",
    heartbeatIntervalIso = "PT30S",
  )

  @Test
  fun `acquireLock returns the saved run when no concurrent run exists`() {
    val captured = argumentCaptor<BackfillRun>()
    whenever(repository.saveAndFlush(captured.capture())).thenAnswer { captured.firstValue }

    val run = runner.acquireLock("mirror", triggeredBy = "test")

    assertThat(run).isNotNull
    assertThat(run!!.backfillId).isEqualTo("mirror")
    assertThat(run.status).isEqualTo(BackfillRunStatus.RUNNING)
    assertThat(run.triggeredBy).isEqualTo("test")
  }

  @Test
  fun `acquireLock returns null when the unique partial index rejects a concurrent run`() {
    whenever(repository.saveAndFlush(any<BackfillRun>()))
      .thenThrow(DataIntegrityViolationException("duplicate key value violates unique constraint"))

    val run = runner.acquireLock("mirror", triggeredBy = null)

    assertThat(run).isNull()
  }

  @Test
  fun `runAsync processes batches, advances cursor and marks COMPLETED on empty batch`() {
    val backfill = StubBackfill(
      batches = listOf(
        BackfillBatch(listOf("a", "b", "c"), "cursor-1"),
        BackfillBatch(listOf("d"), "cursor-2"),
        BackfillBatch(emptyList(), "cursor-2"),
      ),
    )
    val runId = UUID.randomUUID()
    whenever(repository.finishIfRunning(any(), any(), any(), any(), any(), anyOrNull())).thenReturn(1)

    runner.runAsync(runId, backfill)

    assertThat(backfill.processedItems).containsExactlyInAnyOrder("a", "b", "c", "d")

    val status = argumentCaptor<BackfillRunStatus>()
    val processed = argumentCaptor<Long>()
    val failed = argumentCaptor<Long>()
    verify(repository).finishIfRunning(eq(runId), status.capture(), processed.capture(), failed.capture(), any(), anyOrNull())
    assertThat(status.firstValue).isEqualTo(BackfillRunStatus.COMPLETED)
    assertThat(processed.firstValue).isEqualTo(4)
    assertThat(failed.firstValue).isEqualTo(0)
  }

  @Test
  fun `runAsync isolates per-item failures and counts them without aborting the run`() {
    val backfill = StubBackfill(
      batches = listOf(
        BackfillBatch(listOf("ok", "fail", "ok"), "c1"),
        BackfillBatch(emptyList(), "c1"),
      ),
      failOn = setOf("fail"),
    )
    val runId = UUID.randomUUID()
    whenever(repository.finishIfRunning(any(), any(), any(), any(), any(), anyOrNull())).thenReturn(1)

    runner.runAsync(runId, backfill)

    val status = argumentCaptor<BackfillRunStatus>()
    val processed = argumentCaptor<Long>()
    val failed = argumentCaptor<Long>()
    verify(repository).finishIfRunning(eq(runId), status.capture(), processed.capture(), failed.capture(), any(), anyOrNull())
    assertThat(status.firstValue).isEqualTo(BackfillRunStatus.COMPLETED)
    assertThat(processed.firstValue).isEqualTo(2)
    assertThat(failed.firstValue).isEqualTo(1)
  }

  @Test
  fun `runAsync marks the run FAILED when selectBatch throws`() {
    val backfill = StubBackfill(batches = emptyList(), throwOnSelect = IllegalStateException("DB down"))
    val runId = UUID.randomUUID()
    whenever(repository.finishIfRunning(any(), any(), any(), any(), any(), anyOrNull())).thenReturn(1)

    runner.runAsync(runId, backfill)

    val status = argumentCaptor<BackfillRunStatus>()
    val reason = argumentCaptor<String>()
    verify(repository).finishIfRunning(eq(runId), status.capture(), any(), any(), any(), reason.capture())
    assertThat(status.firstValue).isEqualTo(BackfillRunStatus.FAILED)
    assertThat(reason.firstValue).contains("DB down")
  }

  @Test
  fun `runAsync aborts without overwriting when the run is reclaimed mid-flight`() {
    val ownershipLost = CountDownLatch(1)
    val backfill = StubBackfill(
      batches = listOf(
        BackfillBatch(listOf("a", "b"), "c1"),
        BackfillBatch(listOf("c", "d"), "c2"),
        BackfillBatch(emptyList(), "c2"),
      ),
      gate = ownershipLost,
    )
    val runId = UUID.randomUUID()
    whenever(repository.touchHeartbeat(any(), any(), anyOrNull(), any(), any())).thenAnswer {
      ownershipLost.countDown()
      0
    }

    val fastRunner = BackfillRunner(repository, store, staleThresholdIso = "PT5M", heartbeatIntervalIso = "PT0.02S")
    fastRunner.runAsync(runId, backfill)

    verify(repository, never()).finishIfRunning(any(), any(), any(), any(), any(), anyOrNull())
  }

  @Test
  fun `sweepStaleRuns delegates to repository with computed threshold`() {
    whenever(repository.reclaimStaleRunning(any(), any())).thenReturn(2)

    val swept = runner.sweepStaleRuns()

    assertThat(swept).isEqualTo(2)
    verify(repository).reclaimStaleRunning(any(), any())
  }

  @Test
  fun `sweepStaleRuns returns zero when nothing is stale`() {
    whenever(repository.reclaimStaleRunning(any(), any())).thenReturn(0)
    assertThat(runner.sweepStaleRuns()).isEqualTo(0)
  }

  private class StubBackfill(
    private val batches: List<BackfillBatch<String>>,
    private val failOn: Set<String> = emptySet(),
    private val throwOnSelect: Throwable? = null,
    private val gate: CountDownLatch? = null,
  ) : Backfill<String> {
    override val id = "stub"
    override val concurrency = 2
    val processedItems = mutableListOf<String>()
    private var batchIndex = 0

    override fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<String> {
      throwOnSelect?.let { throw it }
      val batch = batches.getOrNull(batchIndex++) ?: BackfillBatch(emptyList(), cursor)
      return batch
    }

    override fun process(item: String) {
      gate?.await(5, TimeUnit.SECONDS)
      if (item in failOn) throw RuntimeException("scripted failure for $item")
      synchronized(processedItems) { processedItems.add(item) }
    }
  }
}
