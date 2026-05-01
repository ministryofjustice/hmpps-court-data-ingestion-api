CREATE TABLE court_document_view
(
    id                      UUID                            NOT NULL constraint court_document_view_pk PRIMARY KEY,
    court_document_id       UUID                            NOT NULL,
    username                varchar(255)                    NOT NULL,
    viewed_at               timestamp with time zone        NOT NULL,
    CONSTRAINT fk_court_document_view_court_document_case_id
        FOREIGN KEY (court_document_id) REFERENCES court_document(id)
);