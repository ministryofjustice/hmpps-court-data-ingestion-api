package uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.format.FormatModelRegistry

class FormatModelAlignmentTest {

  private val registry = FormatModelRegistry.fromResources(
    mapper = jacksonObjectMapper(),
    activeKey = ACTIVE_KEY,
    resourcePaths = listOf(RESOURCE_PATH),
  )

  @Test
  fun `active model resolves to the expected id and version`() {
    val model = registry.active()
    assertEquals("prison-court-register", model.id)
    assertEquals(1, model.version)
    assertTrue(model.labels.isNotEmpty(), "model has no labels")
  }

  @Test
  fun `every label bin is a canonical region anchor`() {
    val model = registry.active()
    model.labels.forEach {
      assertTrue(
        it.xBin in model.canonicalBins,
        "label '${it.canonicalText}' bin ${it.xBin} is not a canonical region ${model.canonicalBins}",
      )
    }
  }

  @Test
  fun `no duplicate labels`() {
    val dupes = registry.active().labels
      .groupingBy { it.canonicalText }.eachCount().filterValues { it > 1 }
    assertTrue(dupes.isEmpty(), "duplicate labels: ${dupes.keys}")
  }

  @Test
  fun `block labels are a non-empty subset of all labels`() {
    val model = registry.active()
    val all = model.labels.map { it.canonicalText }.toSet()
    assertTrue(model.blockLabels.isNotEmpty(), "no per-offence block labels declared")
    assertTrue(model.blockLabels.all { it in all }, "block label not present in label set")
  }

  @Test
  fun `scrub patterns compile and strip page-boundary noise`() {
    val cleaned = registry.active()
      .scrub("MAGISTRATES; Page 2; Register generated on: 07/05/2026; LJA: Greater Manchester")
    assertEquals("MAGISTRATES", cleaned)
  }

  @Test
  fun `contextual labels are declared`() {
    assertTrue(registry.active().contextualLabels.contains("status"))
  }

  companion object {
    private const val ACTIVE_KEY = "prison-court-register:v1"
    private const val RESOURCE_PATH = "formats/prison-court-register-v1.json"
  }
}
