DROP INDEX CONCURRENTLY IF EXISTS idx_court_document_unaddressed_email_norm;

CREATE INDEX CONCURRENTLY idx_court_document_unaddressed_email_norm
    ON court_document (lower(trim(prison_email_address)))
    INCLUDE (prison_email_address, ingestion_at, prisoner_number, court_document_type, addressed_organisation)
    WHERE addressed_prison IS NULL;
