package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.springframework.stereotype.Component

@Component
class BackfillRegistry(backfills: List<Backfill<*>>) {

  init {
    val duplicates = backfills.groupingBy { it.id }.eachCount().filter { it.value > 1 }.keys
    require(duplicates.isEmpty()) { "Duplicate backfill ids: $duplicates" }
  }

  private val byId: Map<String, Backfill<*>> = backfills.associateBy { it.id }

  fun get(id: String): Backfill<*>? = byId[id]

  fun ids(): Set<String> = byId.keys
}
