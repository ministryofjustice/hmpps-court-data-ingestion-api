package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.CourtRegisterApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.CourtRegisterApiMockServer.Companion.TEST_HMCTS_COURTHOUSE_ID_NO_REGISTER
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsCourtScheduleApiExtension.Companion.hmctsCourtScheduleApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtHearingRepository

@Transactional(readOnly = true)
class CourtRegisterApiBackfillTest : IntegrationTestBase() {

  @Autowired
  private lateinit var mapper: ObjectMapper

  @Autowired
  private lateinit var courtHearingRepository: CourtHearingRepository

  @Autowired
  private lateinit var backfill: CourtRegisterApiBackfill

  @BeforeEach
  fun setup() {
    courtDocumentRepository.deleteAll()
  }

  @ParameterizedTest
  @CsvSource(
    "true,0",
    "false,1",
  )
  fun `selectBatch when data from Court Register {isAvailable}, then should {expected} pending records for backfill Court Register Data`(isCourtRegisterDataAvailable: Boolean, expected: Int) {
    // Setup
    if (!isCourtRegisterDataAvailable) {
      hmctsCourtScheduleApi.stubCourtScheduleWithoutCourtRegistry()
    }

    sendSubscriptionNotification(MATCHING_CORE_PERSON)

    // Run tests
    val batch = backfill.selectBatch(cursor = "", batchSize = 200)

    // Check results
    assertThat(batch.items).hasSize(expected)
    assertThat(batch.items.isEmpty()).isEqualTo(isCourtRegisterDataAvailable)
  }

  @Test
  fun `Test receiving a message from the queue will lookup data`() {
    // Setup
    hmctsCourtScheduleApi.stubCourtScheduleWithoutCourtRegistry()
    sendSubscriptionNotification(MATCHING_CORE_PERSON)
    // Mock the missing Court Register, will return 200 now instead
    CourtRegisterApiExtension.courtRegisterApi.stubHmctsCourt(TEST_HMCTS_COURTHOUSE_ID_NO_REGISTER)

    val batch = backfill.selectBatch(cursor = "", batchSize = 200)
    val documentId = batch.items[0]

    // Run test
    runBackfill("court-register-api")

    // Check results
    val fileAfter = courtDocumentRepository.findById(documentId).get()

    assertThat(fileAfter.courtHearing).isNotNull
    assertThat(fileAfter.courtHearing!!.courtCode).isEqualTo("LND001")
    assertThat(fileAfter.courtHearing!!.courtName).isEqualTo("Central London County Court")
    assertThat(fileAfter.courtHearing!!.courtId.toString()).isEqualTo(TEST_HMCTS_COURTHOUSE_ID_NO_REGISTER)
    assertThat(fileAfter.courtHearing!!.hearingType).isEqualTo("First hearing")
  }
}
