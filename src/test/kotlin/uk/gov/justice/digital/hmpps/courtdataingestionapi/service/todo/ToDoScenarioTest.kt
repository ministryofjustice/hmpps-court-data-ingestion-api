package uk.gov.justice.digital.hmpps.courtdataingestionapi.service.todo

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.opentest4j.TestAbortedException
import uk.gov.justice.digital.hmpps.courtdataingestionapi.TestUtil
import java.io.File

// Data-driven harness. Every JSON file in test resources /todo-scenarios becomes a test:
// declare documents (status, duplicateOf, view events) and an expected block, and this runs
// the count model and asserts the outcome.
//
// Lifecycle marker: a file named "*.pending.json" is known to fail against the code as it
// stands and is reported as skipped, not run, with its "pending" reason. Re-introduce it by
// renaming to drop ".pending".

class ToDoScenarioTest {

  @TestFactory
  fun scenarios(): List<DynamicTest> {
    val directory = scenarioDirectory()
    val files = directory.listFiles { f -> f.isFile && f.name.endsWith(".json") }
      ?.sortedBy { it.name }
      ?: error("No scenario files found in $directory")

    return files.map { file ->
      DynamicTest.dynamicTest(file.name) { runScenario(file) }
    }
  }

  private fun runScenario(file: File) {
    if (file.name.endsWith(".pending.json")) {
      val reason = runCatching { load(file).pending }.getOrNull()
        ?: "known to fail; not run until the fix is in place"
      throw TestAbortedException("PENDING: $reason")
    }

    val scenario = load(file)
    rejectSameInstantEvents(scenario)
    ToDoScenarioEngine.statusAware = false
    ToDoScenarioEngine.activeStatuses = setOf("LIVE")
    val result = ToDoScenarioEngine.run(scenario)

    assertThat(result.todoCount)
      .describedAs("todoCount for scenario '%s'", scenario.name)
      .isEqualTo(scenario.expected.todoCount)

    scenario.expected.newUuids?.let { expectedNew ->
      assertThat(result.newUuids)
        .describedAs("counted (new) documents for scenario '%s'", scenario.name)
        .containsExactlyInAnyOrderElementsOf(expectedNew)
    }
  }

  private fun rejectSameInstantEvents(scenario: ToDoScenario) {
    scenario.documents.forEach { document ->
      val times = document.viewEvents.map { it.at }
      require(times.size == times.toSet().size) {
        "Scenario '${scenario.name}': document ${document.uuid} has two view events at the same instant, which is ambiguous"
      }
    }
  }

  private fun load(file: File): ToDoScenario = TestUtil.objectMapper().readValue(file, ToDoScenario::class.java)

  private fun scenarioDirectory(): File {
    val url = javaClass.getResource("/todo-scenarios") ?: error("/todo-scenarios resource directory is missing")
    return File(url.toURI())
  }
}
