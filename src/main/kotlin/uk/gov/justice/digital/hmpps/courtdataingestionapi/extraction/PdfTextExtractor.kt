package uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Component

@Component
class PdfTextExtractor {

  fun extractText(bytes: ByteArray): String? {
    if (!hasHeader(bytes)) return null
    return runCatching {
      Loader.loadPDF(bytes).use { pdf ->
        PDFTextStripper().getText(pdf)?.trim()?.takeIf { it.isNotBlank() }
      }
    }.getOrNull()
  }

  private fun hasHeader(bytes: ByteArray): Boolean = bytes.size >= PDF_HEADER.size &&
    bytes.copyOfRange(0, PDF_HEADER.size).contentEquals(PDF_HEADER)

  companion object {
    private val PDF_HEADER = byteArrayOf(
      '%'.code.toByte(),
      'P'.code.toByte(),
      'D'.code.toByte(),
      'F'.code.toByte(),
    )
  }
}
