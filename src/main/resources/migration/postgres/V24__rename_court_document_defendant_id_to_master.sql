-- court_document.defendant_id has always held the HMCTS masterDefendantId (see V9 comment),
-- so the name was misleading. Rename to match its documented meaning. Value-preserving:
-- RENAME COLUMN keeps data, constraints and the comment; the index is renamed to match.
ALTER TABLE court_document RENAME COLUMN defendant_id TO master_defendant_id;
ALTER INDEX court_document_defendant_id RENAME TO court_document_master_defendant_id;

COMMENT ON COLUMN court_document.master_defendant_id IS
    'External identifier from HMCTS (masterDefendantId). Not a foreign key.';
