package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

interface Backfill<T> {
  val id: String

  val concurrency: Int get() = 1

  fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<T>

  fun process(item: T)
}

data class BackfillBatch<T>(
  val items: List<T>,
  val nextCursor: String,
)
