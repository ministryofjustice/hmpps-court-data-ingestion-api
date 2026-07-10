package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.DestinationType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType
import java.time.LocalDateTime
import java.util.UUID

class CourtDocumentEntityTest {

  @Test
  fun `toString reports only scalar fields, not the lazy associations`() {
    val id = UUID.randomUUID()
    val prisonDocumentId = UUID.randomUUID()
    val hearingId = UUID.randomUUID()

    val entity = CourtDocumentEntity(
      id = id,
      masterDefendantId = UUID.randomUUID(),
      hmctsCourtDocumentId = UUID.randomUUID(),
      prisonDocumentId = prisonDocumentId,
      hmctsCourtHearingId = hearingId,
      prisonEmailAddress = "OMU.HolmeHouse@justice.gov.uk",
      eventType = HmctsEventType.WEE_SendingToCrownCourtForTrial,
      courtDocumentType = CourtDocumentType.PRISON_COURT_REGISTER,
      documentGeneratedTimestamp = LocalDateTime.now(),
      extractedTextSha256 = "some-hash",
      deliverySource = DestinationType.PRISON,
    )

    val result = entity.toString()

    assertThat(result).contains(id.toString())
    assertThat(result).contains(prisonDocumentId.toString())
    assertThat(result).contains(hearingId.toString())
    assertThat(result).contains("PRISON_COURT_REGISTER")
    assertThat(result).contains("some-hash")
    assertThat(result).doesNotContain("courtHearing=")
    assertThat(result).doesNotContain("courtDocumentCases=")
    assertThat(result).doesNotContain("courtDocumentViews=")
  }
}
