package uk.gov.justice.digital.hmpps.courtdataingestionapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.Subscription

interface SubscriptionRepository : JpaRepository<Subscription, String> {
  fun findByEnvironment(environment: String): Subscription?
}
