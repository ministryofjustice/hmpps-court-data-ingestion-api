package uk.gov.justice.digital.hmpps.courtdataingestionapi.service.extraction

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PdfSignatureTest {

  @Test
  fun `recognises a pdf by its signature`() {
    assertThat("%PDF-1.7\nrest of file".toByteArray(Charsets.ISO_8859_1).looksLikePdf()).isTrue()
  }

  @Test
  fun `rejects other formats`() {
    assertThat("PK\u0003\u0004".toByteArray().looksLikePdf()).isFalse() // docx / zip
    assertThat(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()).looksLikePdf()).isFalse() // jpeg
    assertThat("application/octet-stream junk".toByteArray().looksLikePdf()).isFalse()
  }

  @Test
  fun `rejects empty, short, or late-signature input without throwing`() {
    assertThat(ByteArray(0).looksLikePdf()).isFalse()
    assertThat("%PD".toByteArray().looksLikePdf()).isFalse()
    assertThat("junk-then-%PDF-1.4".toByteArray().looksLikePdf()).isFalse()
  }
}
