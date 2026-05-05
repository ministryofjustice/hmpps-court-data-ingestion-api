CREATE TABLE court_document
(
    id                                      UUID                            NOT NULL constraint court_document_pk PRIMARY KEY,
    defendant_id                            UUID                            NOT NULL,
    court_document_id                       UUID                            NOT NULL,
    prison_document_id                      UUID                            NOT NULL,
    ingestion_at                            timestamp with time zone        NOT NULL,
    document_generated_timestamp            timestamp with time zone        NOT NULL,
    prison_email_address                    varchar(255)                    NOT NULL,
    event_type                              varchar(255)                    NOT NULL,
    prisoner_number                         varchar(255)                    NULL,
    identified_at                           timestamp with time zone        NULL
);
CREATE INDEX court_document_defendant_id ON court_document(defendant_id);
CREATE INDEX court_document_prison_document_id ON court_document(prison_document_id);
CREATE INDEX court_document_prisoner_number ON court_document(prisoner_number);


CREATE TABLE court_document_case
(
    id                      UUID                            NOT NULL constraint court_document_case_pk PRIMARY KEY,
    court_document_id       UUID                            NOT NULL,
    case_reference          varchar(255)                    NOT NULL,
    CONSTRAINT fk_court_document_case_court_document_case_id
        FOREIGN KEY (court_document_id) REFERENCES court_document(id)
);