package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.CourtRegisterApiMockServer.Companion.TEST_HMCTS_COURTHOUSE_ID_NO_REGISTER
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsCourtScheduleApiExtension.Companion.hmctsCourtScheduleApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsSubcriptionApiMockServer.Companion.TEST_HMCTS_COURTHOUSE_ID

class HmctsStructuredDataApiEnricherIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var mapper: ObjectMapper

  @Test
  fun `Test receiving a message from the queue will lookup data`() {
    sendSubscriptionNotification(MATCHING_CORE_PERSON)

    val file = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(MATCHING_CORE_PERSON)!!

    assertThat(file.courtHearing).isNotNull
    assertThat(file.courtHearing!!.hmctsCourtId.toString()).isEqualTo(TEST_HMCTS_COURTHOUSE_ID)
    assertThat(file.courtHearing!!.hearingType).isEqualTo("First hearing")
    assertThat(file.courtHearing!!.courtName).isEqualTo("Central London County Court")
    assertThat(file.courtHearing!!.hmppsCourtId).isEqualTo("LND001")
  }

  @Test
  fun `Test when receiving a message from the queue with a hearing with no matching court on court register, then will lookup data and courtCode will be left empty`() {
    hmctsCourtScheduleApi.stubCourtScheduleWithoutCourtRegistry()
    sendSubscriptionNotification(MATCHING_CORE_PERSON)

    val file = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(MATCHING_CORE_PERSON)!!

    assertThat(file.courtHearing).isNotNull
    assertThat(file.courtHearing!!.hmctsCourtId.toString()).isEqualTo(TEST_HMCTS_COURTHOUSE_ID_NO_REGISTER)
    assertThat(file.courtHearing!!.hearingType).isEqualTo("First hearing")
    assertThat(file.courtHearing!!.courtName).isEqualTo("Central London County Court")
    assertThat(file.courtHearing!!.hmppsCourtId).isNull()
  }

  @Test
  fun `Test receiving a message from the queue will replace existing hearing `() {
    sendSubscriptionNotification(MATCHING_CORE_PERSON)
    val fileOne = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(MATCHING_CORE_PERSON)!!
    val created = fileOne.courtHearing!!.createdAt
    sendSubscriptionNotification(MATCHING_CORE_PERSON)
    val fileTwo = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(MATCHING_CORE_PERSON)!!
    val updated = fileTwo.courtHearing!!.updatedAt
    assertThat(updated > created).isTrue
    assertThat(fileOne.courtHearing!!.id).isEqualTo(fileTwo.courtHearing!!.id)
  }
}
