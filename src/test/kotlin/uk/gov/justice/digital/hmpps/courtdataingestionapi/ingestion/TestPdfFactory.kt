package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream

object TestPdfFactory {
  fun singlePagePdf(text: String): ByteArray {
    PDDocument().use { document ->
      val page = PDPage()
      document.addPage(page)

      PDPageContentStream(document, page).use { content ->
        content.beginText()
        content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
        content.newLineAtOffset(72f, 720f)
        content.showText(text)
        content.endText()
      }

      val output = ByteArrayOutputStream()
      document.save(output)
      return output.toByteArray()
    }
  }
}
