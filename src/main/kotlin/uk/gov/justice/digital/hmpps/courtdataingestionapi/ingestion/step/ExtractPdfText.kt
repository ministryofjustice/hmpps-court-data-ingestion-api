package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.step

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionEnricher

@Component
@Order(300)
class ExtractPdfText : IngestionEnricher {

  override fun enrich(context: IngestionContext): IngestionContext {
    val fileBytes = context.downloadedFileBytes ?: return context

    if (!hasPdfHeaderSignature(fileBytes)) {
      return context.withWarning(WARNING_NOT_PDF)
    }

    return try {
      val pdf = Loader.loadPDF(fileBytes)
      pdf.use { pdf ->
        val extracted = PDFTextStripper().getText(pdf)?.trim()

        if (extracted.isNullOrBlank()) {
          context.withWarning(WARNING_NO_TEXT)
        } else {
          context.copy(extractedText = extracted)
        }
      }
    } catch (e: Exception) {
      context.withWarning("PDF text extraction failed: ${e.message}")
    }
  }

  private fun hasPdfHeaderSignature(bytes: ByteArray): Boolean = bytes.size >= PDF_HEADER.size && bytes.copyOfRange(0, PDF_HEADER.size).contentEquals(PDF_HEADER)

  private fun IngestionContext.withWarning(warning: String): IngestionContext = copy(warnings = warnings + warning)

  companion object {
    private const val WARNING_NOT_PDF = "PDF text extraction skipped because file was not recognised as PDF"
    private const val WARNING_NO_TEXT = "PDF text extraction produced no text"
    private val PDF_HEADER = byteArrayOf(
      '%'.code.toByte(),
      'P'.code.toByte(),
      'D'.code.toByte(),
      'F'.code.toByte(),
    )
  }
}
