CREATE TABLE extraction_result
(
    id                  UUID                        NOT NULL CONSTRAINT extraction_result_pk PRIMARY KEY,
    document_id         UUID                        NOT NULL,
    content_sha256      CHAR(64)                    NOT NULL,
    format_id           VARCHAR(100)                NOT NULL,
    format_version      INTEGER                     NOT NULL,
    extractor_version   VARCHAR(100)                NOT NULL,
    status              VARCHAR(20)                 NOT NULL,
    page_count          INTEGER                     NULL,
    field_count         INTEGER                     NULL,
    result              JSONB                       NOT NULL,
    extracted_at        TIMESTAMP WITH TIME ZONE    NOT NULL,
    CONSTRAINT extraction_result_unique
        UNIQUE (document_id, format_id, format_version, extractor_version)
);

CREATE INDEX extraction_result_document_id ON extraction_result(document_id);
CREATE INDEX extraction_result_content_sha256 ON extraction_result(content_sha256);
CREATE INDEX extraction_result_result_gin ON extraction_result USING GIN (result);
