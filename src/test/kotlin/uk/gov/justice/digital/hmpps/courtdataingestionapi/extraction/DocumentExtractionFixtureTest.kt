package uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.format.DocumentExtractor
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.format.ExtractedDocument
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.format.FormatModelRegistry


class DocumentExtractionFixtureTest {

  private val registry = FormatModelRegistry.fromResources(
    mapper = jacksonObjectMapper(),
    activeKey = "prison-court-register:v1",
    resourcePaths = listOf("formats/prison-court-register-v1.json"),
  )

  private val result: ExtractedDocument by lazy {
    val model = registry.active()
    val stream = checkNotNull(
      javaClass.getResourceAsStream("/test-fixtures/moriarty-register.pdf"),
    ) { "Test fixture PDF not found on classpath at /test-fixtures/moriarty-register.pdf" }
    stream.use { DocumentExtractor.extract(it, "moriarty-register.pdf", model) }
  }

  // ── header: defendant fields ──────────────────────────────────────────────

  @Test fun `extracts defendant name`() =
    assertEquals("MORIARTY, James", result.headerFields["defendant name"])

  @Test fun `extracts date of birth`() =
    assertEquals("01/01/1860", result.headerFields["date of birth"])

  @Test fun `extracts age`() =
    assertEquals("35", result.headerFields["age"])

  @Test fun `extracts gender`() =
    assertEquals("Male", result.headerFields["gender"])

  @Test fun `extracts nationality`() =
    assertEquals("British", result.headerFields["nationality"])

  @Test fun `extracts aliases`() =
    assertEquals("THE NAPOLEON OF CRIME", result.headerFields["aliases"])

  @Test fun `extracts defendant address`() =
    assertEquals("23 Conduit Street, London", result.headerFields["defendant address"])

  @Test fun `extracts offences count`() =
    assertEquals("3", result.headerFields["offences"])

  // ── header: case fields ───────────────────────────────────────────────────

  @Test fun `extracts case reference`() =
    assertEquals("06MC0001825", result.headerFields["case reference"])

  @Test fun `extracts date of hearing`() =
    assertEquals("01/05/2026", result.headerFields["date of hearing"])

  @Test fun `extracts asn`() =
    assertEquals("2526MC0600000001M", result.headerFields["asn"])

  @Test fun `extracts defendant attendance`() =
    assertEquals("In person", result.headerFields["defendant attendance"])

  @Test fun `extracts prosecutor`() =
    assertEquals("Metropolitan Police", result.headerFields["prosecutor"])

  @Test fun `extracts jurisdiction`() =
    assertEquals("MAGISTRATES", result.headerFields["jurisdiction"])

  @Test fun `extracts hearing type`() =
    assertEquals("Crown Court Sending", result.headerFields["hearing type"])

  @Test fun `extracts post-hearing custody status`() =
    assertEquals("Remanded in custody", result.headerFields["post-hearing custody status"])

  @Test fun `extracts officer in case`() =
    assertEquals("DI G Lestrade", result.headerFields["officer in case"])

  // ── header: counsel with status disambiguation ────────────────────────────

  @Test fun `extracts prosecution counsel`() =
    assertEquals("S Holmes KC", result.headerFields["prosecution counsel"])

  @Test fun `disambiguates prosecution counsel status`() =
    assertEquals("Prosecution", result.headerFields["prosecution counsel status"])

  @Test fun `extracts defence counsel`() =
    assertEquals("I Adler KC", result.headerFields["defence counsel"])

  @Test fun `disambiguates defence counsel status`() =
    assertEquals("Defence", result.headerFields["defence counsel status"])

  // ── offence blocks ────────────────────────────────────────────────────────

  @Test fun `produces three offence blocks`() =
    assertEquals(3, result.offenceBlocks.size)

  @Test fun `first offence is conspiracy to commit murder`() {
    val block = offenceByCode("FA68001")
    assertNotNull(block, "offence FA68001 not found")
    assertEquals("CONSPIRACY TO COMMIT MURDER", block!!["offence title"])
    assertEquals("NOT GUILTY", block["plea"])
    assertEquals("NOT GUILTY", block["indicated plea"])
    assertEquals("GUILTY", block["verdict"])
    assertEquals("01/05/2026", block["conviction date"])
    assertTrue(block["result text"]!!.startsWith("RI - Remanded in custody"))
  }

  @Test fun `second offence is theft of the Mazarin Stone`() {
    val block = offenceByCode("TH68001")
    assertNotNull(block, "offence TH68001 not found")
    assertEquals("THEFT", block!!["offence title"])
    assertEquals("GUILTY", block["verdict"])
  }

  @Test fun `third offence is blackmail`() {
    val block = offenceByCode("BL68001")
    assertNotNull(block, "offence BL68001 not found")
    assertEquals("BLACKMAIL", block!!["offence title"])
    assertEquals("GUILTY", block["plea"])
  }

  @Test fun `every offence block has the nine expected fields`() {
    val required = setOf(
      "offence code", "offence title", "wording", "plea", "indicated plea",
      "verdict", "allocation decision", "conviction date", "result text",
    )
    result.offenceBlocks.forEachIndexed { i, block ->
      required.forEach { field ->
        assertTrue(block.containsKey(field), "block[$i] missing '$field'")
      }
    }
  }

  // ── structural ────────────────────────────────────────────────────────────

  @Test fun `document has two pages`() =
    assertEquals(2, result.pageCount)

  @Test fun `all 24 header labels are present`() {
    val expected = setOf(
      "defendant name", "defendant address", "date of birth", "age", "gender",
      "nationality", "aliases", "offences", "parent or guardian", "parent or guardian address",
      "case reference", "date of hearing", "asn", "defendant attendance", "attending solicitor",
      "prosecutor", "jurisdiction", "hearing type", "post-hearing custody status",
      "officer in case", "prosecution counsel", "prosecution counsel status",
      "defence counsel", "defence counsel status",
    )
    val missing = expected - result.headerFields.keys.toSet()
    assertTrue(missing.isEmpty(), "Missing header fields: $missing")
  }

  private fun offenceByCode(code: String) =
    result.offenceBlocks.firstOrNull { it["offence code"] == code }
}
