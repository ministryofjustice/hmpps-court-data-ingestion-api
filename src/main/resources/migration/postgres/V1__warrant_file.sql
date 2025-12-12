CREATE TABLE warrant_file
(
    id                     UUID                            NOT NULL constraint warrant_file_pk PRIMARY KEY,
    defendant_id           UUID                            NOT NULL,
    external_file_id       varchar(255)                    NOT NULL,
    ingestion_at           timestamp with time zone        NOT NULL
);
CREATE INDEX warrant_file_defendant_id ON warrant_file(defendant_id);
