ALTER TABLE court_document
    rename column court_document_id to hmcts_court_document_id;
ALTER TABLE court_document
    rename column court_hearing_id to hmcts_court_hearing_id;

TRUNCATE TABLE COURT_HEARING;

ALTER TABLE court_document
    ADD COLUMN court_hearing_id UUID NULL CONSTRAINT court_documnet_court_hearing_fk REFERENCES court_hearing(id);

ALTER TABLE court_hearing
    ADD COLUMN hearing_date timestamp with time zone  NOT NULL;

ALTER TABLE court_hearing
    ADD COLUMN hmcts_court_hearing_id UUID NOT NULL;