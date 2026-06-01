
package uk.gov.justice.digital.hmpps.courtdataingestionapi.prisonemail

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PrisonEmailNormaliserTest {

  @Test
  fun `trims, lowercases and strips mailto prefix`() {
    assertThat(PrisonEmailNormaliser.normalise("  MailTo:OMU.Example@Example.COM ")).isEqualTo("omu.example@example.com")
  }

  @Test
  fun `removes non-breaking spaces`() {
    assertThat(PrisonEmailNormaliser.normalise("omu\u00A0@example.com")).isEqualTo("omu@example.com")
  }

  @Test
  fun `returns null for null, blank or whitespace only`() {
    assertThat(PrisonEmailNormaliser.normalise(null)).isNull()
    assertThat(PrisonEmailNormaliser.normalise("")).isNull()
    assertThat(PrisonEmailNormaliser.normalise("   ")).isNull()
  }
}
