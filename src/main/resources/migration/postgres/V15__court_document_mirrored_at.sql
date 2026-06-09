ALTER TABLE court_document
  ADD COLUMN mirrored_to_doc_store_at TIMESTAMP;

CREATE INDEX court_document_unmirrored
  ON court_document (id)
  WHERE mirrored_to_doc_store_at IS NULL;
