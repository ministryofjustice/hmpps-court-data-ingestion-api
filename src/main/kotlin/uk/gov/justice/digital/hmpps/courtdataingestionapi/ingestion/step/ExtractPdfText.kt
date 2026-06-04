package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(300)
class ExtractPdfText : IngestionEnricher {

  override fun enrich(context: IngestionContext): IngestionContext {
    val fileBytes = context.downloadedFileBytes ?: return context

    if (!looksLikePdf(fileBytes)) {
      return context.copy(
        warnings = context.warnings + "PDF text extraction skipped because file was not recognised as PDF",
      )
    }

    return try {
      val pdf = Loader.loadPDF(fileBytes)
      try {
        val extracted = PDFTextStripper().getText(pdf)?.trim()

        if (extracted.isNullOrBlank()) {
          context.copy(
            warnings = context.warnings + "PDF text extraction produced no text",
          )
        } else {
          context.copy(extractedText = extracted)
        }
      } finally {
        pdf.close()
      }
    } catch (e: Exception) {
      context.copy(
        warnings = context.warnings + "PDF text extraction failed: ${e.message}",
      )
    }
  }

  private fun looksLikePdf(bytes: ByteArray): Boolean = bytes.size >= 4 &&
    bytes[0] == '%'.code.toByte() &&
    bytes[1] == 'P'.code.toByte() &&
    bytes[2] == 'D'.code.toByte() &&
    bytes[3] == 'F'.code.toByte()
}
