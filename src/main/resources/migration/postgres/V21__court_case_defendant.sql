CREATE TABLE court_case_defendant
(
    defendant_id         uuid                     NOT NULL CONSTRAINT court_case_defendant_pk PRIMARY KEY,
    case_reference       varchar(255)             NOT NULL,
    master_defendant_id  uuid                     NOT NULL,
    name                 varchar(255)             NULL,
    date_of_birth        date                     NULL,
    retrieved_at         timestamp                NOT NULL,
    source               varchar(64)              NOT NULL
);

CREATE INDEX court_case_defendant_master ON court_case_defendant (master_defendant_id);
CREATE INDEX court_case_defendant_case_reference ON court_case_defendant (case_reference);
