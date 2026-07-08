ALTER TABLE court_document
    ADD COLUMN metadata_version INTEGER NOT NULL DEFAULT 0;

CREATE INDEX court_document_metadata_version
    ON court_document (metadata_version);

UPDATE court_document SET metadata_version = 1 WHERE mirrored_to_doc_store_at IS NOT NULL;