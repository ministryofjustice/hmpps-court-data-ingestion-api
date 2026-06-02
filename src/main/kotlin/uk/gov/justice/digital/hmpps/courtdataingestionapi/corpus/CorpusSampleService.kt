package uk.gov.justice.digital.hmpps.courtdataingestionapi.corpus

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.engine.PdfExtractor
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.extraction.looksLikePdf
import java.io.ByteArrayInputStream
import kotlin.math.roundToInt

@Service
class CorpusSampleService(
  private val repository: CorpusSampleRepository,
  private val documentApi: HmppsDocumentManagementApi,
) {
  fun sample(
    eventType: String?,
    courtDocumentType: String?,
    size: Int,
    binSize: Int,
    seed: Double?,
  ): CorpusSample {
    require(eventType == null || eventType in EVENT_TYPES) { "Unknown eventType '$eventType'" }
    require(courtDocumentType == null || courtDocumentType in DOCUMENT_TYPES) { "Unknown courtDocumentType '$courtDocumentType'" }
    require(binSize in 1..1000) { "binSize must be between 1 and 1000" }
    require(seed == null || seed in -1.0..1.0) { "seed must be between -1.0 and 1.0" }
    val clamped = size.coerceIn(1, MAX_SIZE)

    val rows = repository.sample(eventType, courtDocumentType, clamped, seed)
    log.info(
      "Corpus sample requested eventType={} courtDocumentType={} size={} seed={} -> {} rows",
      eventType,
      courtDocumentType,
      clamped,
      seed,
      rows.size,
    )

    val documents = rows.map { represent(it, binSize) }
    return CorpusSample(
      filter = CorpusFilter(eventType, courtDocumentType),
      binSize = binSize,
      requestedSize = clamped,
      returnedCount = documents.size,
      seed = seed,
      documents = documents,
    )
  }

  private fun represent(row: CorpusRow, binSize: Int): CorpusDocument {
    val base = CorpusDocument(
      courtDocumentId = row.courtDocumentId,
      prisonDocumentId = row.prisonDocumentId,
      eventType = row.eventType,
      courtDocumentType = row.courtDocumentType,
      documentGeneratedTimestamp = row.documentGeneratedTimestamp,
      ingestionAt = row.ingestionAt,
      status = CorpusStatus.OK,
    )

    val bytes = try {
      documentApi.downloadFile(row.prisonDocumentId)
    } catch (ex: Exception) {
      log.warn("Corpus download failed for {}", row.prisonDocumentId, ex)
      return base.copy(status = CorpusStatus.DOWNLOAD_ERROR, error = ex.message ?: ex.javaClass.simpleName)
    }

    if (!bytes.looksLikePdf()) {
      return base.copy(status = CorpusStatus.NON_PDF, error = "missing %PDF- signature")
    }

    val parsed = PdfExtractor.extract(ByteArrayInputStream(bytes), row.prisonDocumentId.toString())
    parsed.error?.let { return base.copy(status = CorpusStatus.PARSE_ERROR, error = it, pages = parsed.pages) }

    val lines = parsed.lines.map { line ->
      CorpusLine(
        page = line.page,
        y = round1(line.y),
        cells = line.cells.map { cell ->
          CorpusCell(
            text = cell.text,
            x = round1(cell.x),
            xEnd = round1(cell.xEnd),
            xBin = (cell.x / binSize).toInt(),
          )
        },
      )
    }
    val observedBins = lines.asSequence()
      .flatMap { it.cells.asSequence() }
      .map { it.xBin }
      .distinct()
      .sorted()
      .toList()

    return base.copy(
      status = CorpusStatus.OK,
      pages = parsed.pages,
      lineCount = lines.size,
      observedXBins = observedBins,
      lines = lines,
    )
  }

  private fun round1(v: Float): Float = (v * 10f).roundToInt() / 10f

  companion object {
    private val log = LoggerFactory.getLogger(CorpusSampleService::class.java)
    private const val MAX_SIZE = 200
    private val EVENT_TYPES = HmctsEventType.entries.map { it.name }.toSet()
    private val DOCUMENT_TYPES = CourtDocumentType.entries.map { it.name }.toSet()
  }
}
