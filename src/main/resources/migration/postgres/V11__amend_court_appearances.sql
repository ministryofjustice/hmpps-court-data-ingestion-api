ALTER TABLE court_document
    ADD COLUMN IF NOT EXISTS downloaded_file_sha256 VARCHAR(64);

ALTER TABLE court_document
    ADD COLUMN IF NOT EXISTS extracted_text_sha256 VARCHAR(64);

ALTER TABLE court_document
    ADD COLUMN IF NOT EXISTS duplicate_of UUID;

CREATE INDEX IF NOT EXISTS idx_court_document_downloaded_file_sha256
    ON court_document (downloaded_file_sha256);

CREATE INDEX IF NOT EXISTS idx_court_document_extracted_text_sha256
    ON court_document (extracted_text_sha256);