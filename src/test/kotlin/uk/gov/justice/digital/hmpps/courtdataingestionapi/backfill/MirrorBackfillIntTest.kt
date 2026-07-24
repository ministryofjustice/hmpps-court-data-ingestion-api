package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsCourtScheduleApiExtension.Companion.hmctsCourtScheduleApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtHearingRepository

class MirrorBackfillIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var mapper: ObjectMapper

  @Autowired
  private lateinit var courtHearingRepository: CourtHearingRepository

  @Autowired
  private lateinit var backfill: MirrorBackfill

  @BeforeEach
  fun setup() {
    courtDocumentRepository.deleteAll()
  }

  @ParameterizedTest
  @CsvSource(
    "0,1",
    "1,1",
    "2,0",
    "3,0",
  )
  fun `selectBatch when data is forced to metadata-version {version}, then should return {expected} pending records for backfill Mirror`(metadataVersion: Int, expected: Int) {
    // Setup
    sendSubscriptionNotification(MATCHING_CORE_PERSON)
    val document = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(MATCHING_CORE_PERSON)!!
    document.metadataVersion = metadataVersion
    courtDocumentRepository.save(document)

    // Run tests
    val batch = backfill.selectBatch(cursor = "", batchSize = 200)

    // Check results
    assertThat(batch.items).hasSize(expected)
    assertThat(batch.items.isEmpty()).isEqualTo(metadataVersion >= METADATA_VERSION)
  }

  @ParameterizedTest
  @CsvSource(
    "0,true,2",
    "1,false,2",
    "2,true,2",
    "3,false,3",
  )
  fun `Test receiving a message from the queue will lookup data`(metadataVersion: Int, isCourtRegisterDataAvailable: Boolean, expected: Int) {
    // Setup
    if (!isCourtRegisterDataAvailable) {
      hmctsCourtScheduleApi.stubCourtScheduleWithoutCourtRegistry()
    }

    sendSubscriptionNotification(MATCHING_CORE_PERSON)
    val documentBefore = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(MATCHING_CORE_PERSON)!!
    documentBefore.metadataVersion = metadataVersion
    courtDocumentRepository.save(documentBefore)

    // Run test
    runBackfill("mirror")

    // Check results
    val fileAfter = courtDocumentRepository.findById(documentBefore.id).get()

    assertThat(fileAfter.metadataVersion).isEqualTo(expected)
    assertThat(fileAfter.metadataVersion).isGreaterThanOrEqualTo(METADATA_VERSION)
  }

  companion object {
    const val METADATA_VERSION: Int = 2
  }
}
