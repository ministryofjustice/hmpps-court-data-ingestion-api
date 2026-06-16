package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.enumeration

enum class DocumentSearchOrderBy(
  val property: String,
) {
  FILENAME("filename"),
  FILE_EXTENSION("fileExtension"),
  FILESIZE("fileSize"),
  CREATED_TIME("createdTime"),
}
