CREATE INDEX idx_court_document_by_day_query
    ON court_document (prisoner_number, ingestion_at DESC);