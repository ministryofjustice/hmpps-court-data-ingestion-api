package uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.format

import java.util.TreeSet

enum class LabelScope { HEADER, BLOCK }

data class LabelSpec(
  val canonicalText: String,
  val xBin: Int,
  val scope: LabelScope,
)

data class FormatModel(
  val id: String,
  val version: Int,
  val description: String = "",

  val xBinSize: Int = 10,
  val binTolerance: Int = 1,
  val intraLineSeparator: String = " ",
  val crossLineSeparator: String = "; ",

  val contextualLabels: Set<String> = emptySet(),

  val valueScrubPatterns: List<String> = emptyList(),

  val labels: List<LabelSpec> = emptyList(),
) {
  val canonicalBins: TreeSet<Int> = TreeSet(labels.map { it.xBin })

  val labelBinByText: Map<String, Int> = labels.associate { it.canonicalText to it.xBin }

  val blockLabels: Set<String> = labels.asSequence()
    .filter { it.scope == LabelScope.BLOCK }
    .map { it.canonicalText }
    .toSet()

  private val scrubRegexes: List<Regex> = valueScrubPatterns.map {
    Regex(it, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
  }

  fun scrub(value: String): String = scrubRegexes.fold(value) { acc, rx -> rx.replace(acc, "") }.trim()

  fun xBin(x: Float): Int = (x / xBinSize).toInt()

  val key: String get() = "$id:v$version"

  init {
    require(id.isNotBlank()) { "FormatModel.id must not be blank" }
    require(version >= 1) { "FormatModel.version must be >= 1" }
    require(labels.isNotEmpty()) { "FormatModel '$key' has no labels" }
    val dupes = labels.groupingBy { it.canonicalText }.eachCount().filterValues { it > 1 }.keys
    require(dupes.isEmpty()) { "FormatModel '$key' has duplicate labels: $dupes" }
  }
}
