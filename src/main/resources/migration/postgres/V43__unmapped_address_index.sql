DROP INDEX CONCURRENTLY IF EXISTS idx_court_document_unmapped_address;

CREATE INDEX CONCURRENTLY idx_court_document_unmapped_address
    ON court_document (lower(trim(prison_email_address)))
    INCLUDE (prison_email_address, ingestion_at, prisoner_number, court_document_type)
    WHERE delivery_mapping_id IS NULL;

DROP INDEX CONCURRENTLY IF EXISTS idx_court_document_unaddressed_email_norm;

DROP INDEX CONCURRENTLY IF EXISTS idx_court_document_unclassified_address;
