package uk.gov.justice.digital.hmpps.courtdataingestionapi.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class ToInstantTest {

  @Test
  fun `should correctly convert BST timestamp to UTC`() {
    val input = "2026-06-12T17:00:00"

    val instant = input.toUtcInstant()

    assertThat(instant).isEqualTo(Instant.parse("2026-06-12T16:00:00Z"))
  }

  @Test
  fun `should respect explicit UTC timestamp`() {
    val input = "2026-06-12T17:00:00Z"

    val instant = input.toUtcInstant()

    assertThat(instant).isEqualTo(Instant.parse("2026-06-12T17:00:00Z"))
  }

  @Test
  fun `should correctly convert GMT timestamp to UTC`() {
    val input = "2026-01-12T17:00:00"

    val instant = input.toUtcInstant()

    assertThat(instant).isEqualTo(Instant.parse("2026-01-12T17:00:00Z"))
  }
}
