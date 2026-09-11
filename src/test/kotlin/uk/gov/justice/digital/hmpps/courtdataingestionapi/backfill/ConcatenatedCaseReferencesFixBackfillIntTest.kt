package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentCaseEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.DestinationType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtHearingRepository
import java.time.LocalDateTime
import java.util.UUID

class ConcatenatedCaseReferencesFixBackfillIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var mapper: ObjectMapper

  @Autowired
  private lateinit var courtHearingRepository: CourtHearingRepository

  @Autowired
  private lateinit var backfill: ConcatenatedCaseReferencesFixBackfill

  @BeforeEach
  fun setup() {
    courtDocumentRepository.deleteAll()
  }

  @ParameterizedTest
  @MethodSource("getSelectBatchCourtDocumentBackfillTestParameters")
  @Transactional
  fun `selectBatch test when passing document with set case references, should return {expected} total item`(cases: List<List<String>>, expected: Int) {
    // Setup mocked data
    cases.forEach { it ->
      val extraDoc = copyCourtDocument()
      addCourtDocumentCases(extraDoc, it)
      courtDocumentRepository.save(extraDoc)
    }

    // Run test
    val batch = backfill.selectBatch(cursor = "", batchSize = 100)

    // Check results
    assertThat(batch.items).hasSize(expected)

    if (expected > 0) {
      val result = courtDocumentRepository.findById(batch.items.first()).get()
      assertThat(result.courtDocumentCases).hasSizeGreaterThan(0)
      assertThat(result.courtDocumentCases.filter { it.caseReference.contains(",") }).hasSizeGreaterThan(0)
    }
  }

  @ParameterizedTest
  @MethodSource("getSelectBatchCourtDocumentBackfillTestParameters")
  @Transactional
  fun `processBatch test when passing document with set case references, should return {expected} total item`(cases: List<List<String>>, expected: Int) {
    // Setup mocked data
    cases.forEach {
      val extraDoc = copyCourtDocument()
      addCourtDocumentCases(extraDoc, it)
      courtDocumentRepository.save(extraDoc)
    }

    // Run test
    val batch = backfill.selectBatch(cursor = "", batchSize = 100)
    assertThat(batch.items).hasSize(expected)

    if (expected == 0) return

    val before = courtDocumentRepository.findById(batch.items.first()).get()
    assertThat(before.courtDocumentCases.filter { it.caseReference.contains(",") }).hasSizeGreaterThan(0)

    backfill.process(batch.items.first())

    // Check results
    val result = courtDocumentRepository.findById(batch.items.first()).get()
    assertThat(result.courtDocumentCases.filter { it.caseReference.contains(",") }).isEmpty()
  }

  // TODO (CDIA-327): Add full cycle integration tests

  companion object {
    const val METADATA_VERSION: Int = 2

    private val courtDocument: CourtDocumentEntity = CourtDocumentEntity(
      id = UUID.randomUUID(),
      masterDefendantId = UUID.randomUUID(),
      hmctsCourtDocumentId = UUID.randomUUID(),
      prisonDocumentId = UUID.randomUUID(),
      hmctsCourtHearingId = null,
      prisonEmailAddress = "mock@backfill.test",
      eventType = HmctsEventType.WEE_Remand,
      courtDocumentType = CourtDocumentType.REMAND_WARRANT,
      documentGeneratedTimestamp = LocalDateTime.now(),
      ingestionAt = LocalDateTime.now(),
//      courtHearing = TODO(),
      courtDocumentCases = mutableListOf(),
      courtDocumentViews = mutableListOf(),
//      addressedPrison = "mock prison",
//      downloadedFileSha256 = "hash",
//      extractedTextSha256 = "hash",
      deliverySource = DestinationType.PRISON,
      metadataVersion = METADATA_VERSION,
//      metadataUpdatedAt = TODO(),
//      prisonerNumber = TODO(),
//      identifiedAt = TODO(),
//      matchOutcome = TODO(),
    )

    private fun addCourtDocumentCases(courtDocument: CourtDocumentEntity, cases: List<String>) {
      cases.forEach {
        courtDocument.courtDocumentCases.add(
          CourtDocumentCaseEntity(
            id = UUID.randomUUID(),
            caseReference = it,
            courtDocument = courtDocument,
          ),
        )
      }
    }

    private fun copyCourtDocument(): CourtDocumentEntity = courtDocument.copy(
      id = UUID.randomUUID(),
      courtDocumentCases = mutableListOf(),
    )

    // list of case references, expected batch
    val CONCATENATED_CASES_2_ALL_DUPLICATED = listOf("case1", "case2", "case1,case2")
    val CONCATENATED_CASES_0 = listOf("case1", "case2")
    val CONCATENATED_CASES_2_SOME_DUPLICATED = listOf("case1", "case1,case2")
    val CONCATENATED_CASES_2_EXTRA_CASE = listOf("case1", "case3", "case1,case2")

    @JvmStatic
    fun getSelectBatchCourtDocumentBackfillTestParameters() = listOf(
      Arguments.of(listOf(CONCATENATED_CASES_0), 0),

      Arguments.of(listOf(CONCATENATED_CASES_2_ALL_DUPLICATED), 1),
      Arguments.of(listOf(CONCATENATED_CASES_2_SOME_DUPLICATED), 1),
      Arguments.of(listOf(CONCATENATED_CASES_2_EXTRA_CASE), 1),
      Arguments.of(listOf(CONCATENATED_CASES_2_ALL_DUPLICATED, CONCATENATED_CASES_0), 1),
      Arguments.of(listOf(CONCATENATED_CASES_2_SOME_DUPLICATED, CONCATENATED_CASES_0), 1),
      Arguments.of(listOf(CONCATENATED_CASES_2_EXTRA_CASE, CONCATENATED_CASES_0), 1),

      Arguments.of(listOf(CONCATENATED_CASES_2_ALL_DUPLICATED, CONCATENATED_CASES_2_SOME_DUPLICATED), 2),
      Arguments.of(listOf(CONCATENATED_CASES_2_ALL_DUPLICATED, CONCATENATED_CASES_2_EXTRA_CASE), 2),
      Arguments.of(listOf(CONCATENATED_CASES_2_SOME_DUPLICATED, CONCATENATED_CASES_2_EXTRA_CASE), 2),
      Arguments.of(listOf(CONCATENATED_CASES_2_ALL_DUPLICATED, CONCATENATED_CASES_2_SOME_DUPLICATED, CONCATENATED_CASES_0), 2),

      Arguments.of(listOf(CONCATENATED_CASES_2_ALL_DUPLICATED, CONCATENATED_CASES_2_SOME_DUPLICATED, CONCATENATED_CASES_2_EXTRA_CASE), 3),
      Arguments.of(listOf(CONCATENATED_CASES_2_ALL_DUPLICATED, CONCATENATED_CASES_2_SOME_DUPLICATED, CONCATENATED_CASES_2_EXTRA_CASE, CONCATENATED_CASES_0), 3),
    )
  }
}
