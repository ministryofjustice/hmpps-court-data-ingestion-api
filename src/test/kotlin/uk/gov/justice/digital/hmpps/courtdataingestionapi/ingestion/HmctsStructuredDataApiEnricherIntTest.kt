package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsSubcriptionApiMockServer

@Transactional(readOnly = true)
class HmctsStructuredDataApiEnricherIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var mapper: ObjectMapper

  @Test
  fun `Test receiving a message from the queue will lookup data`() {
    sendSubscriptionNotification(MATCHING_CORE_PERSON)

    val file = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(MATCHING_CORE_PERSON)!!

    assertThat(file.courtHearing).isNotNull
    assertThat(file.courtHearing!!.courtId.toString()).isEqualTo(HmctsSubcriptionApiMockServer.TEST_HMCTS_COURTHOUSE_ID)
    assertThat(file.courtHearing!!.hearingType).isEqualTo("First hearing")
    assertThat(file.courtHearing!!.courtName).isEqualTo("Central London County Court")
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
