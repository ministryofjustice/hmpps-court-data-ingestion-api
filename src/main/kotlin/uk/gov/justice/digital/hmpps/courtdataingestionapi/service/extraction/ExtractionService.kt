package uk.gov.justice.digital.hmpps.courtdataingestionapi.service.extraction

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.ExtractionResultEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.format.FormatModel
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.format.FormatModelRegistry
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.ExtractionResultRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.util.Sha256
import java.io.ByteArrayInputStream
import java.util.UUID

@Service
class ExtractionService(
  private val documentApi: HmppsDocumentManagementApi,
  private val repository: ExtractionResultRepository,
  private val formatModels: FormatModelRegistry,
  private val pipeline: ExtractionPipeline,
  private val objectMapper: ObjectMapper,
  @Value("\${extraction.extractor-version:dev}") private val extractorVersion: String,
) {
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun extractAndStore(documentId: UUID): ExtractionResultEntity {
    val model = formatModels.active()

    val existing = findExisting(documentId, model)
    if (existing != null && existing.status != STATUS_ERROR) return existing

    val entity = try {
      val bytes = documentApi.downloadFile(documentId)
      runExtraction(documentId, bytes, Sha256.hex(bytes), model)
    } catch (ex: Exception) {
      log.warn("Transient error fetching document {}, will retry after cooldown", documentId, ex)
      result(documentId, model, EMPTY_SHA, STATUS_ERROR, mapOf("error" to (ex.message ?: ex.javaClass.simpleName)))
    }

    return persist(existing, entity)
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun extractStructuredDataAndStore(
    documentId: UUID,
    pdfBytes: ByteArray,
    downloadedFileSha256: String?,
  ): ExtractionResultEntity {
    val model = formatModels.active()

    val existing = findExisting(documentId, model)
    if (existing != null && existing.status != STATUS_ERROR) return existing
    val sha = downloadedFileSha256 ?: Sha256.hex(pdfBytes)

    return persist(existing, runExtraction(documentId, pdfBytes, sha, model))
  }

  private fun findExisting(documentId: UUID, model: FormatModel) = repository.findByDocumentIdAndFormatIdAndFormatVersionAndExtractorVersion(
    documentId,
    model.id,
    model.version,
    extractorVersion,
  )

  private fun runExtraction(documentId: UUID, bytes: ByteArray, sha: String, model: FormatModel): ExtractionResultEntity {
    if (!bytes.looksLikePdf()) {
      return result(documentId, model, sha, STATUS_SKIPPED, mapOf("reason" to "missing %PDF- signature"))
    }
    return runCatching {
      val out = pipeline.extract(ByteArrayInputStream(bytes), documentId.toString(), model)
      result(
        documentId,
        model,
        sha,
        STATUS_OK,
        mapOf("header" to out.headerFields, "offences" to out.offenceBlocks, "labelSignature" to out.labelSignature),
        out.pageCount,
        out.fieldCount,
      )
    }.getOrElse { ex ->
      log.warn("Extraction failed for document {}", documentId, ex)
      result(documentId, model, sha, STATUS_FAILED, mapOf("error" to (ex.message ?: ex.javaClass.simpleName)))
    }
  }

  private fun result(
    documentId: UUID,
    model: FormatModel,
    sha: String,
    status: String,
    payload: Map<String, Any?>,
    pageCount: Int? = null,
    fieldCount: Int? = null,
  ) = ExtractionResultEntity(
    documentId = documentId,
    contentSha256 = sha,
    formatId = model.id,
    formatVersion = model.version,
    extractorVersion = extractorVersion,
    status = status,
    pageCount = pageCount,
    fieldCount = fieldCount,
    result = objectMapper.writeValueAsString(payload),
  )

  private fun persist(existing: ExtractionResultEntity?, entity: ExtractionResultEntity): ExtractionResultEntity = if (existing != null) {
    repository.save(existing.updateFrom(entity))
  } else {
    try {
      repository.save(entity)
    } catch (race: DataIntegrityViolationException) {
      repository.findByDocumentIdAndFormatIdAndFormatVersionAndExtractorVersion(
        entity.documentId,
        entity.formatId,
        entity.formatVersion,
        entity.extractorVersion,
      )?.let { repository.save(it.updateFrom(entity)) } ?: throw race
    }
  }

  private fun ExtractionResultEntity.updateFrom(source: ExtractionResultEntity) = apply {
    status = source.status
    contentSha256 = source.contentSha256
    pageCount = source.pageCount
    fieldCount = source.fieldCount
    result = source.result
    extractedAt = source.extractedAt
  }

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
    private const val STATUS_OK = "OK"
    private const val STATUS_FAILED = "FAILED"
    private const val STATUS_SKIPPED = "SKIPPED_NON_PDF"
    private const val STATUS_ERROR = "ERROR"
    private const val EMPTY_SHA = "0000000000000000000000000000000000000000000000000000000000000000"
  }
}
