package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class DeliveryMappingEntityTest {

  private fun mapping(prisonCode: String?, category: DeliveryCategory?) = DeliveryMappingEntity(UUID.randomUUID(), "mailbox@justice.gov.uk", prisonCode, category)

  @Test
  fun `a category that uses a prison code gives the mapping's prison`() {
    val prison = DeliveryCategory(code = "PRISON", name = "Prison", requiresPrisonCode = true)

    assertThat(mapping("LEI", prison).destinationPrison).isEqualTo("LEI")
  }

  @Test
  fun `a category that does not use a prison code gives no prison, even if one is set`() {
    val youthCustody = DeliveryCategory(code = "YOUTH_CUSTODY", name = "Youth custody", requiresPrisonCode = false)

    assertThat(mapping("WYI", youthCustody).destinationPrison).isNull()
  }

  @Test
  fun `a mapping with no category gives its prison, as ingestion does`() {
    assertThat(mapping("LEI", null).destinationPrison).isEqualTo("LEI")
  }
}
