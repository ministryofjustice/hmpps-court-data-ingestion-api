package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import java.util.UUID

interface Backfill<T> {
  val id: String

  val concurrency: Int get() = 1

  fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<T>

  fun process(item: T)

  fun parseCursorUUID(cursor: String): UUID = if (cursor.isEmpty()) ZERO_UUID else UUID.fromString(cursor)
  fun parseCursorInt(cursor: String): Int = cursor.toIntOrNull() ?: 0

  companion object {
    private val ZERO_UUID = UUID(0L, 0L)
  }
}

data class BackfillBatch<T>(
  val items: List<T>,
  val nextCursor: String,
)
