ALTER TABLE court_document
    ADD COLUMN match_outcome VARCHAR(64) NULL;

UPDATE court_document
   SET match_outcome = 'MATCHED_ON_MASTER_DEFENDANT_ID'
 WHERE prisoner_number IS NOT NULL;

CREATE INDEX court_document_match_outcome
    ON court_document (match_outcome)
