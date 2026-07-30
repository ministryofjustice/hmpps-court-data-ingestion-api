package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import tools.jackson.databind.node.JsonNodeFactory
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.Document
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentApiType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentSearchRequest
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentSearchResult
import java.time.LocalDateTime
import java.util.UUID

class CdiaDocumentStatusBackfillTest {

  private val documentManagementApi: HmppsDocumentManagementApi = mock()
  private val backfill = CdiaDocumentStatusBackfill(documentManagementApi)

  private val live = linkedSetOf<UUID>()
  private val unconvertable = mutableSetOf<UUID>()

  private fun givenLiveDocuments(count: Int): List<UUID> = (1..count)
    .map { UUID.randomUUID() }
    .also { live.addAll(it) }

  private fun stubApi() {
    whenever(documentManagementApi.search(any())).thenAnswer { invocation ->
      val request = invocation.getArgument<DocumentSearchRequest>(0)
      val ordered = live.toList()
      val from = request.page * request.pageSize
      val page = if (from >= ordered.size) emptyList() else ordered.subList(from, minOf(from + request.pageSize, ordered.size))
      DocumentSearchResult(results = page.map { document(it) }, totalResultsCount = ordered.size.toLong())
    }
    whenever(documentManagementApi.mergeMetadata(any(), any())).thenAnswer { invocation ->
      val uuid = invocation.getArgument<UUID>(0)
      if (uuid in unconvertable) throw IllegalStateException("document-management-api rejected $uuid")
      live.remove(uuid)
      document(uuid)
    }
  }

  private fun runBackfill(batchSize: Int, maxIterations: Int = 1_000): RunResult {
    var cursor = ""
    val attempted = mutableListOf<UUID>()
    var processed = 0
    var failed = 0
    var iterations = 0

    while (iterations < maxIterations) {
      iterations++
      val batch = backfill.selectBatch(cursor, batchSize)
      if (batch.items.isEmpty()) break
      batch.items.forEach { item ->
        attempted.add(item.documentUuid)
        runCatching { backfill.process(item) }
          .onSuccess { processed++ }
          .onFailure { failed++ }
      }
      cursor = batch.nextCursor
    }
    return RunResult(attempted, processed, failed, iterations, terminated = iterations < maxIterations)
  }

  private data class RunResult(
    val attempted: List<UUID>,
    val processed: Int,
    val failed: Int,
    val iterations: Int,
    val terminated: Boolean,
  )

  @Test
  @DisplayName("every document is converted when the population spans several batches")
  fun `a population larger than one batch is fully drained`() {
    val documents = givenLiveDocuments(55)
    stubApi()

    val result = runBackfill(batchSize = 10)

    assertThat(result.terminated).isTrue()
    assertThat(live).isEmpty()
    assertThat(result.processed).isEqualTo(55)
    assertThat(result.failed).isZero()
    assertThat(result.attempted).containsExactlyInAnyOrderElementsOf(documents)
  }

  @Test
  fun `no document is handed to process more than once`() {
    givenLiveDocuments(55)
    stubApi()

    val result = runBackfill(batchSize = 10)

    assertThat(result.attempted).doesNotHaveDuplicates()
  }

  @Test
  fun `a partial batch at the end does not strand the remainder`() {
    givenLiveDocuments(7)
    stubApi()

    val result = runBackfill(batchSize = 10)

    assertThat(live).isEmpty()
    assertThat(result.processed).isEqualTo(7)
    assertThat(result.iterations).isEqualTo(2)
  }

  @Test
  fun `a document that cannot be converted stops the run instead of looping forever`() {
    val documents = givenLiveDocuments(25)
    unconvertable.add(documents.first())
    stubApi()

    val result = runBackfill(batchSize = 10)

    assertThat(result.terminated).isTrue()
    assertThat(result.processed).isEqualTo(24)
    assertThat(result.failed).isEqualTo(1)
    assertThat(result.attempted.count { it == documents.first() }).isEqualTo(1)
    assertThat(live).containsExactly(documents.first())
  }

  @Test
  fun `a second run starts from a clean slate and converts documents left by the first`() {
    val documents = givenLiveDocuments(12)
    unconvertable.add(documents.first())
    stubApi()

    val first = runBackfill(batchSize = 10)
    assertThat(first.processed).isEqualTo(11)
    assertThat(live).containsExactly(documents.first())

    unconvertable.clear()
    val second = runBackfill(batchSize = 10)

    assertThat(second.processed).isEqualTo(1)
    assertThat(live).isEmpty()
  }

  private fun document(uuid: UUID) = Document(
    documentUuid = uuid,
    documentType = DocumentApiType.HMCTS_WARRANT,
    documentFilename = "doc",
    filename = "doc",
    fileExtension = "pdf",
    fileSize = 1,
    fileHash = "raw-$uuid",
    fileContentHash = "content-$uuid",
    mimeType = "application/pdf",
    metadata = JsonNodeFactory.instance.objectNode().apply {
      put("source", "court-data-ingestion-api")
      put("status", "LIVE")
    },
    createdTime = LocalDateTime.now(),
    createdByServiceName = "court-data-ingestion-api",
    createdByUsername = "TEST",
    duplicateOf = null,
  )
}
