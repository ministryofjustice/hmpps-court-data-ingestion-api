ALTER TABLE court_document
    ADD COLUMN unmatched_reason VARCHAR(64) NULL;

CREATE INDEX court_document_unmatched_reason
    ON court_document (unmatched_reason)
    WHERE unmatched_reason IS NOT NULL;
