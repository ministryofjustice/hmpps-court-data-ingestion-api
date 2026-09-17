package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentCaseEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.DestinationType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmppsDocumentManagementApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType
import java.time.LocalDateTime
import java.util.UUID

class ConcatenatedCaseReferencesFixBackfillIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var transactionTemplate: TransactionTemplate

  @Autowired
  private lateinit var backfill: ConcatenatedCaseReferencesFixBackfill

  @BeforeEach
  fun setup() {
    courtDocumentRepository.deleteAll()
  }

  @ParameterizedTest
  @MethodSource("getSelectBatchUnitTestParameters")
  @Transactional
  fun `selectBatch test, when checking documents for concatenated case references, should return {expected} total items for processing`(cases: List<List<String>>, expected: Int) {
    // Setup mocked data
    cases.forEach {
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
      assertThat(result.courtDocumentCases).isNotEmpty()
      assertThat(result.courtDocumentCases.filter { it.caseReference.contains(",") }).isNotEmpty()
    }
  }

  @ParameterizedTest
  @MethodSource("getProcessBatchUnitTestParameters")
  @Transactional
  fun `processBatch test, when a document with concatenated case references is processed, should return no concatenated ones after`(cases: List<String>) {
    // Setup mocked data
    val extraDoc = copyCourtDocument()
    addCourtDocumentCases(extraDoc, cases)
    courtDocumentRepository.saveAndFlush(extraDoc)
    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.stubMergeMetadata(extraDoc.prisonDocumentId)

    // Run test
    backfill.process(extraDoc.id)

    // Check results
    assertThat(extraDoc.courtDocumentCases.filter { it.caseReference.contains(",") }).isNotEmpty()

    val result = courtDocumentRepository.findById(extraDoc.id).get()
    assertThat(result.courtDocumentCases.filter { it.caseReference.contains(",") }).isEmpty()
  }

  @ParameterizedTest
  @MethodSource("getRunBackfillIntegrationTestParameters")
  fun `run {concatenated-cases} backfill test, should find {expectedConcatenated} case references needing to be fixed, and return {expectedFixed} total case references with no concatenated ones after backfill run`(cases: List<String>, expectedConcatenated: Int, expectedFixed: Int) {
    // Setup mocked data
    sendSubscriptionNotificationWaitForRecordToBeCreated(MATCHING_CORE_PERSON)
    val documentBefore = setupCourtDocumentCases(cases)
    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.stubMergeMetadata(documentBefore.prisonDocumentId)

    // Run test
    runBackfill("concatenated-cases")

    // Check results
    assertThat(documentBefore.courtDocumentCases).isNotEmpty()
    assertThat(documentBefore.courtDocumentCases.filter { it.caseReference.contains(",") }).hasSize(expectedConcatenated)

    val results = getCourtDocument(documentBefore.prisonerNumber!!, documentBefore.prisonDocumentId)
    assertThat(results).isNotEmpty()
    assertThat(results.first().caseReferences.filter { it.contains(",") }).isEmpty()
    assertThat(results.first().caseReferences).hasSize(expectedFixed)
  }

  private fun setupCourtDocumentCases(cases: List<String>): CourtDocumentEntity = transactionTemplate.execute {
    val document = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(MATCHING_CORE_PERSON)!!
    addCourtDocumentCases(document, cases)
    courtDocumentRepository.save(document)
    document
  }

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
      courtDocument.courtDocumentCases.toMutableList()
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
    fun getSelectBatchUnitTestParameters() = listOf(
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

    @JvmStatic
    fun getProcessBatchUnitTestParameters() = listOf(
      Arguments.of(CONCATENATED_CASES_2_ALL_DUPLICATED),
      Arguments.of(CONCATENATED_CASES_2_SOME_DUPLICATED),
      Arguments.of(CONCATENATED_CASES_2_EXTRA_CASE),
    )

    // Remember to add 1 to the total expected after fix because of default case reference added by sendSubscription
    @JvmStatic
    fun getRunBackfillIntegrationTestParameters() = listOf(
      Arguments.of(CONCATENATED_CASES_0, 0, 3),
      Arguments.of(CONCATENATED_CASES_2_ALL_DUPLICATED, 1, 3),
      Arguments.of(CONCATENATED_CASES_2_SOME_DUPLICATED, 1, 3),
      Arguments.of(CONCATENATED_CASES_2_EXTRA_CASE, 1, 4),

      Arguments.of(listOf("case1,case2", "case1,case2"), 2, 3),
      Arguments.of(listOf("case1,case2", "case2,case1"), 2, 3),
      Arguments.of(listOf("case1,case2", "case2,case3"), 2, 4),
      Arguments.of(listOf("case1,case2", "case3,case4"), 2, 5),
      Arguments.of(listOf("case1", "case1,case2", "case3,case4"), 2, 5),
      Arguments.of(listOf("case1,case2", "case3,case4", "case5"), 2, 6),
      Arguments.of(listOf("case1", "case1,case2", "case3,case4", "case5"), 2, 6),
    )
  }
}
