package uk.gov.justice.digital.hmpps.courtdataingestionapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.DeliveryCategory

@Repository
interface DeliveryCategoryRepository : JpaRepository<DeliveryCategory, String> {
  fun findByCode(code: String): DeliveryCategory?
}
