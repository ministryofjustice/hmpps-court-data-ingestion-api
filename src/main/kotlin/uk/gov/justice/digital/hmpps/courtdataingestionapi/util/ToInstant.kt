package uk.gov.justice.digital.hmpps.courtdataingestionapi.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

private val DEFAULT_ZONE = ZoneId.of("Europe/London")

fun String.toUtcInstant(): Instant = try {
  OffsetDateTime.parse(this).toInstant()
} catch (_: DateTimeParseException) {
  LocalDateTime.parse(this)
    .atZone(DEFAULT_ZONE)
    .toInstant()
}
