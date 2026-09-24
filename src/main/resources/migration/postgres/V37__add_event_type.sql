ALTER TABLE court_document_view_event
DROP CONSTRAINT ck_court_document_view_event_type;

ALTER TABLE court_document_view_event
    ADD CONSTRAINT ck_court_document_view_event_type
        CHECK (event_type IN ('VIEWED', 'MARKED_NEW', 'WARRANT_PROCESSED'));