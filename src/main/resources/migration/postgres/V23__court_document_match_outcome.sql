-- Records the outcome of the Core Person Record lookup for every processed notification: how a
-- match was reached, or why one was not. Nullable (null = not yet processed), so adding the column
-- is metadata-only with no table rewrite.
ALTER TABLE court_document
    ADD COLUMN match_outcome VARCHAR(64) NULL;

-- Backfill existing matches. Historically both the forward and back-match paths wrote a
-- prisoner_number without recording which produced it, so the path cannot be reconstructed: every
-- historical match is labelled MATCHED_ON_MASTER_DEFENDANT_ID as a best guess. Only rows processed
-- after this ships carry an accurate outcome.
UPDATE court_document
   SET match_outcome = 'MATCHED_ON_MASTER_DEFENDANT_ID'
 WHERE prisoner_number IS NOT NULL;

-- Partial index over the non-matched outcomes only: those are the set worth querying ("which
-- documents did not match, and why"), and it keeps the index small regardless of table size.
-- Listed explicitly rather than by a NOT LIKE 'MATCHED\_%' pattern, which silently depends on the
-- enum naming convention holding forever.
CREATE INDEX court_document_match_outcome
    ON court_document (match_outcome)
    WHERE match_outcome IN ('NO_CORE_PERSON', 'NO_PRISON_NUMBER', 'MULTIPLE_PRISON_NUMBERS');
