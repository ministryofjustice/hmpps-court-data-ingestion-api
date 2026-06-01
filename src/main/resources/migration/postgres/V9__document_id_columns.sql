-- Documentation only. No schema change, safe on a deployed database.
-- Records which id is the join anchor and which ids are external (and from where),
-- so the column names alone no longer mislead about what joins to what.

COMMENT ON COLUMN court_document.id IS
    'Surrogate primary key. The only column other tables should foreign-key to.';
COMMENT ON COLUMN court_document.court_document_id IS
    'External identifier from HMCTS (notification documentId). Not a foreign key. Do not join on this.';
COMMENT ON COLUMN court_document.prison_document_id IS
    'External identifier from the HMPPS Document Management store. Not a foreign key.';
COMMENT ON COLUMN court_document.defendant_id IS
    'External identifier from HMCTS (masterDefendantId). Not a foreign key.';
COMMENT ON COLUMN court_document.prison_email_address IS
    'Delivery email address from the HMCTS notification. Matched against prison_email_mapping.email to derive addressed_prison.';

COMMENT ON COLUMN court_document_case.id IS 'Surrogate primary key.';
COMMENT ON COLUMN court_document_case.court_document_id IS
    'Foreign key to court_document.id. Despite the name, this references the surrogate primary key, not court_document.court_document_id.';

COMMENT ON COLUMN court_document_view.id IS 'Surrogate primary key.';
COMMENT ON COLUMN court_document_view.court_document_id IS
    'Foreign key to court_document.id. Despite the name, this references the surrogate primary key, not court_document.court_document_id.';
