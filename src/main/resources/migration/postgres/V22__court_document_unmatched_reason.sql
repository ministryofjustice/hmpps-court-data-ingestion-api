-- Makes the determination blind spot recoverable. Determination is prisoner_number presence, but
-- the "CPR returned more than one prison number" case writes no prisoner_number and was previously
-- only logged, collapsing "no prisoner" and "ambiguous" into a single NULL. Recording the reason
-- keeps the ambiguous set (the co-defendant / bad-master population) queryable rather than invisible.
--
-- Nullable with no default, so this is a metadata-only change with no table rewrite or long lock on a
-- large table. Keep it nullable: adding NOT NULL or a populating DEFAULT later would force a rewrite.
ALTER TABLE court_document
    ADD COLUMN unmatched_reason VARCHAR(64) NULL;

-- Partial index: only the unmatched minority is queried ("which documents are unmatched and why"),
-- so this stays small regardless of table size rather than indexing every matched row's NULL.
CREATE INDEX court_document_unmatched_reason
    ON court_document (unmatched_reason)
    WHERE unmatched_reason IS NOT NULL;
