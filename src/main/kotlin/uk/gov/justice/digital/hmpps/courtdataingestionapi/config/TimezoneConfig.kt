package uk.gov.justice.digital.hmpps.courtdataingestionapi.config

import java.time.ZoneId

class TimezoneConfig {
  companion object {
    private const val TIMEZONE_NAME = "Europe/London"
    val TIMEZONE: ZoneId = ZoneId.of(TIMEZONE_NAME)
  }
}
