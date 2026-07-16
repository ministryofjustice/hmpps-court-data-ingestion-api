package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "court_document_view_event")
data class CourtDocumentViewEntity(
  @Id
  val id: UUID = UUID.randomUUID(),
  val username: String,
  var occurredAt: LocalDateTime = LocalDateTime.now(),
  @Enumerated(EnumType.STRING)
  var eventType: CourtDocumentViewEventType = CourtDocumentViewEventType.VIEWED,
  @ManyToOne
  var courtDocument: CourtDocumentEntity? = null,
)
