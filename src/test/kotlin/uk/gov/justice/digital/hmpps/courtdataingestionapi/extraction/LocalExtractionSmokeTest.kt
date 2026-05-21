package uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.format.DocumentExtractor
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.format.FormatModelRegistry
import java.io.File

@Tag("local")
class LocalExtractionSmokeTest {

  private val sampleDir: File =
    (
      System.getProperty("extractionSampleDir")
        ?: System.getenv("EXTRACTION_SAMPLE_DIR")
        ?: "src/test/resources/local-samples"
      ).let(::File)

  private val model = FormatModelRegistry.fromResources(
    mapper = jacksonObjectMapper(),
    activeKey = "prison-court-register:v1",
    resourcePaths = listOf("formats/prison-court-register-v1.json"),
  ).active()

  @TestFactory
  fun `extract each sample pdf`(): List<DynamicTest> {
    val pdfs = sampleDir.listFiles { f -> f.isFile && f.extension.equals("pdf", ignoreCase = true) }
      ?.sortedBy { it.name }
      .orEmpty()

    assumeTrue(pdfs.isNotEmpty(), "No sample PDFs in ${sampleDir.absolutePath}; skipping local extraction test")

    return pdfs.map { pdf ->
      DynamicTest.dynamicTest(pdf.name) {
        pdf.inputStream().use { input ->
          val out = DocumentExtractor.extract(input, pdf.name, model)
          assertTrue(out.headerFields.isNotEmpty(), "no header fields extracted from ${pdf.name}")
          assertTrue(out.fieldCount > 0, "no fields extracted from ${pdf.name}")
          println(
            "${pdf.name}: ${out.headerFields.size} header fields, " +
              "${out.offenceBlocks.size} offences, ${out.labelSignature.size} labels",
          )
        }
      }
    }
  }
}
