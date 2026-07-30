package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import tools.jackson.databind.node.JsonNodeFactory
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.Document
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentApiType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentMetadataStatus
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import java.time.LocalDateTime
import java.util.UUID

class ThingsToDoServiceTest {

  private val courtDocumentRepository: CourtDocumentRepository = mock()
  private val notificationService: PrisonDocumentNotificationService = mock()
  private val documentApi: HmppsDocumentManagementApi = mock()

  private val service = ThingsToDoService(courtDocumentRepository, notificationService, documentApi)

  @Nested
  @DisplayName("No duplicates")
  inner class NoDuplicates {

    @Test
    fun `no documents at all is nothing to do`() {
      scenario(courtDocs = emptyList(), unread = emptySet(), apiReturns = emptyList())
      assertThat(count()).isEqualTo(0)
    }

    @Test
    fun `a single unread document counts once`() {
      val a = uuid()
      scenario(courtDocs = listOf(courtDoc(a)), unread = setOf(a), apiReturns = listOf(dmsDoc(a)))
      assertThat(count()).isEqualTo(1)
    }

    @Test
    fun `a single read document counts zero`() {
      val a = uuid()
      scenario(courtDocs = listOf(courtDoc(a)), unread = emptySet(), apiReturns = listOf(dmsDoc(a)))
      assertThat(count()).isEqualTo(0)
    }

    @Test
    fun `distinct unread documents each count`() {
      val a = uuid()
      val b = uuid()
      scenario(
        courtDocs = listOf(courtDoc(a), courtDoc(b)),
        unread = setOf(a, b),
        apiReturns = listOf(dmsDoc(a), dmsDoc(b)),
      )
      assertThat(count()).isEqualTo(2)
    }
  }

  @Nested
  @DisplayName("Duplicates the documents API has linked via duplicateOf")
  inner class WhenTheApiReportsDuplicateOf {

    @Test
    fun `a linked pair, both unread, collapses to one`() {
      val original = uuid()
      val copy = uuid()
      scenario(
        courtDocs = listOf(courtDoc(original), courtDoc(copy)),
        unread = setOf(original, copy),
        apiReturns = listOf(dmsDoc(original), dmsDoc(copy, duplicateOf = original)),
      )
      assertThat(count()).isEqualTo(1)
    }

    @Test
    fun `original unread, copy read, counts the original once`() {
      val original = uuid()
      val copy = uuid()
      scenario(
        courtDocs = listOf(courtDoc(original), courtDoc(copy)),
        unread = setOf(original),
        apiReturns = listOf(dmsDoc(original), dmsDoc(copy, duplicateOf = original)),
      )
      assertThat(count()).isEqualTo(1)
    }

    @Test
    fun `original read, copy unread, counts zero (the user has already seen the document)`() {
      // The unread filter drops the original, so only the copy's UUID is looked up.
      // The copy carries duplicateOf, so it is excluded. The group vanishes, which is
      // correct: viewing any copy means the document has been seen.
      val original = uuid()
      val copy = uuid()
      scenario(
        courtDocs = listOf(courtDoc(original), courtDoc(copy)),
        unread = setOf(copy),
        apiReturns = listOf(dmsDoc(original), dmsDoc(copy, duplicateOf = original)),
      )
      assertThat(count()).isEqualTo(0)
    }

    @Test
    fun `a chain of three copies collapses to one`() {
      val original = uuid()
      val copyB = uuid()
      val copyC = uuid()
      scenario(
        courtDocs = listOf(courtDoc(original), courtDoc(copyB), courtDoc(copyC)),
        unread = setOf(original, copyB, copyC),
        apiReturns = listOf(
          dmsDoc(original),
          dmsDoc(copyB, duplicateOf = original),
          dmsDoc(copyC, duplicateOf = original),
        ),
      )
      assertThat(count()).isEqualTo(1)
    }

    @Test
    fun `two independent duplicate pairs count two`() {
      val originalX = uuid()
      val copyX = uuid()
      val originalY = uuid()
      val copyY = uuid()
      scenario(
        courtDocs = listOf(courtDoc(originalX), courtDoc(copyX), courtDoc(originalY), courtDoc(copyY)),
        unread = setOf(originalX, copyX, originalY, copyY),
        apiReturns = listOf(
          dmsDoc(originalX),
          dmsDoc(copyX, duplicateOf = originalX),
          dmsDoc(originalY),
          dmsDoc(copyY, duplicateOf = originalY),
        ),
      )
      assertThat(count()).isEqualTo(2)
    }
  }

  @Nested
  @DisplayName("Reproduction: documents API omits duplicateOf")
  inner class WhenTheApiOmitsDuplicateOf {

    @Test
    fun `a real duplicate pair is counted twice when duplicateOf is null`() {
      val original = uuid()
      val copy = uuid()
      scenario(
        courtDocs = listOf(courtDoc(original), courtDoc(copy)),
        unread = setOf(original, copy),
        apiReturns = listOf(dmsDoc(original), dmsDoc(copy, duplicateOf = null)),
      )
      assertThat(count()).isEqualTo(2)
    }

    @Test
    fun `four duplicate pairs inflate a true eight to twelve`() {
      val distinct = List(6) { uuid() }
      val pairs = List(4) { uuid() to uuid() }
      val all = distinct + pairs.flatMap { listOf(it.first, it.second) }
      scenario(
        courtDocs = all.map { courtDoc(it) },
        unread = all.toSet(),
        apiReturns = distinct.map { dmsDoc(it) } +
          pairs.flatMap { listOf(dmsDoc(it.first), dmsDoc(it.second, duplicateOf = null)) },
      )
      assertThat(count()).isEqualTo(14)
    }
  }

  @Nested
  @DisplayName("Edge cases")
  inner class EdgeCases {

    @Test
    fun `an unread document missing from the documents API is not counted`() {
      val a = uuid()
      scenario(courtDocs = listOf(courtDoc(a)), unread = setOf(a), apiReturns = emptyList())
      assertThat(count()).isEqualTo(0)
    }

    @Test
    fun `a copy whose original is not in the unread set is still excluded`() {
      val absentOriginal = uuid()
      val copy = uuid()
      scenario(
        courtDocs = listOf(courtDoc(copy)),
        unread = setOf(copy),
        apiReturns = listOf(dmsDoc(copy, duplicateOf = absentOriginal)),
      )
      assertThat(count()).isEqualTo(0)
    }

    @Test
    fun `the documents API returning an unrelated document is ignored`() {
      val requested = uuid()
      val stray = uuid()
      scenario(
        courtDocs = listOf(courtDoc(requested)),
        unread = setOf(requested),
        apiReturns = listOf(dmsDoc(requested), dmsDoc(stray)),
      )
      assertThat(count()).isEqualTo(1)
    }
  }

  private fun count() = service.getToDoList(PRISONER).thingsToDo.size

  private fun scenario(courtDocs: List<CourtDocumentEntity>, unread: Set<UUID>, apiReturns: List<Document>) {
    whenever(notificationService.getUnreadDocumentDateFrom(PRISONER)).thenReturn(DATE_FROM)
    whenever(courtDocumentRepository.findByPrisonerNumber(PRISONER)).thenReturn(courtDocs)
    whenever(notificationService.isUnread(any(), eq(DATE_FROM))).thenAnswer { invocation ->
      invocation.getArgument<CourtDocumentEntity>(0).prisonDocumentId in unread
    }
    whenever(documentApi.findByDocumentUuids(any())).thenAnswer { invocation ->
      val requested = invocation.getArgument<Collection<UUID>>(0)
      apiReturns.filter { it.documentUuid in requested }
    }
  }

  private fun courtDoc(uuid: UUID): CourtDocumentEntity = CourtDocumentEntity(
    masterDefendantId = UUID.randomUUID(),
    hmctsCourtDocumentId = UUID.randomUUID(),
    prisonDocumentId = uuid,
    hmctsCourtHearingId = null,
    prisonEmailAddress = "test@example.com",
    eventType = HmctsEventType.WEE_CustodialSentence,
    courtDocumentType = CourtDocumentType.COMMON_PLATFORM_DOCUMENT,
    documentGeneratedTimestamp = LocalDateTime.now(),
  )

  @Nested
  @DisplayName("Only documents the Documents tab would show are counted")
  inner class OnlyDisplayableDocuments {

    @Test
    fun `a legacy LIVE document is not counted because the tab does not show it`() {
      val a = uuid()
      scenario(courtDocs = listOf(courtDoc(a)), unread = setOf(a), apiReturns = listOf(dmsDoc(a, status = "LIVE")))
      assertThat(count()).isEqualTo(0)
    }

    @Test
    fun `an AWAITING document is not counted`() {
      val a = uuid()
      scenario(
        courtDocs = listOf(courtDoc(a)),
        unread = setOf(a),
        apiReturns = listOf(dmsDoc(a, status = DocumentMetadataStatus.AWAITING.name)),
      )
      assertThat(count()).isEqualTo(0)
    }

    @Test
    fun `a DELETED document is not counted even when not soft deleted`() {
      val a = uuid()
      scenario(
        courtDocs = listOf(courtDoc(a)),
        unread = setOf(a),
        apiReturns = listOf(dmsDoc(a, status = DocumentMetadataStatus.DELETED.name)),
      )
      assertThat(count()).isEqualTo(0)
    }

    @Test
    fun `a document with no status metadata at all is not counted`() {
      val a = uuid()
      scenario(courtDocs = listOf(courtDoc(a)), unread = setOf(a), apiReturns = listOf(dmsDoc(a, status = null)))
      assertThat(count()).isEqualTo(0)
    }

    @Test
    fun `a type the Documents tab does not request is not counted`() {
      val a = uuid()
      scenario(
        courtDocs = listOf(courtDoc(a)),
        unread = setOf(a),
        apiReturns = listOf(dmsDoc(a, documentType = DocumentApiType.APPEAL_ORDER)),
      )
      assertThat(count()).isEqualTo(0)
    }

    @Test
    fun `a mixed set counts only the displayable documents`() {
      val active = uuid()
      val legacy = uuid()
      val hiddenType = uuid()
      scenario(
        courtDocs = listOf(courtDoc(active), courtDoc(legacy), courtDoc(hiddenType)),
        unread = setOf(active, legacy, hiddenType),
        apiReturns = listOf(
          dmsDoc(active),
          dmsDoc(legacy, status = "LIVE"),
          dmsDoc(hiddenType, documentType = DocumentApiType.BREACH_ORDER),
        ),
      )
      assertThat(count()).isEqualTo(1)
    }
  }

  private fun dmsDoc(
    uuid: UUID,
    duplicateOf: UUID? = null,
    status: String? = DocumentMetadataStatus.ACTIVE.name,
    documentType: DocumentApiType = DocumentApiType.PRISON_COURT_REGISTER,
  ) = Document(
    documentUuid = uuid,
    documentType = documentType,
    documentFilename = "doc",
    filename = "doc",
    fileExtension = "pdf",
    fileSize = 1,
    fileHash = "raw-$uuid",
    fileContentHash = "content-$uuid",
    mimeType = "application/pdf",
    metadata = JsonNodeFactory.instance.objectNode().apply {
      put("prisonNumber", PRISONER)
      status?.let { put("status", it) }
    },
    createdTime = LocalDateTime.now(),
    createdByServiceName = "court-data-ingestion-api",
    createdByUsername = "TEST",
    duplicateOf = duplicateOf,
  )

  private fun uuid(): UUID = UUID.randomUUID()

  private companion object {
    const val PRISONER = "A3242ED"
    val DATE_FROM: LocalDateTime = LocalDateTime.parse("2026-01-01T00:00")
  }
}
