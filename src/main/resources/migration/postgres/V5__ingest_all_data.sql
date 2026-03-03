CREATE TABLE warrant_file_case
(
    id                      UUID                            NOT NULL constraint warrant_file_case_pk PRIMARY KEY,
    warrant_file_id         UUID                            NOT NULL,
    case_reference          varchar(255)                    NOT NULL,

    CONSTRAINT fk_warrant_file_case_warrant_file_id
        FOREIGN KEY (warrant_file_id) REFERENCES warrant_file(id)
);


ALTER TABLE warrant_file ADD COLUMN defendant_name varchar(255);
ALTER TABLE warrant_file ADD COLUMN defendant_date_of_birth timestamp with time zone;
ALTER TABLE warrant_file ADD COLUMN prison_email_address varchar(255);
ALTER TABLE warrant_file ADD COLUMN document_generated_timestamp timestamp with time zone;