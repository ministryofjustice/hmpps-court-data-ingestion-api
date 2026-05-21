package uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.format

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.InputStream

class FormatModelRegistry(
  private val models: Map<String, FormatModel>,
  private val activeKey: String,
) {
  fun active(): FormatModel = models.getValue(activeKey)

  fun byKey(id: String, version: Int): FormatModel? = models["$id:v$version"]

  fun all(): Collection<FormatModel> = models.values

  fun selectFor(@Suppress("UNUSED_PARAMETER") observedLabels: Set<String> = emptySet()): FormatModel = active()

  companion object {
    fun fromResources(
      mapper: ObjectMapper,
      activeKey: String,
      resourcePaths: List<String>,
      classLoader: ClassLoader = FormatModelRegistry::class.java.classLoader,
    ): FormatModelRegistry {
      val models = resourcePaths.associate { path ->
        val stream: InputStream = classLoader.getResourceAsStream(path)
          ?: error("Format model resource not found on classpath: $path")
        val model = stream.use { mapper.readValue(it, FormatModel::class.java) }
        model.key to model
      }
      require(models.containsKey(activeKey)) {
        "Active format '$activeKey' not among loaded models ${models.keys}"
      }
      return FormatModelRegistry(models, activeKey)
    }
  }
}
