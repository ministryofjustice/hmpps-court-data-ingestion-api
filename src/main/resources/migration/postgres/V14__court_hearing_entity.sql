CREATE TABLE court_hearing
(
    id                      UUID                            NOT NULL constraint court_hearing_pk PRIMARY KEY,
    court_id                UUID                            NOT NULL,
    court_name              varchar(255)                    NOT NULL,
    hearing_type            varchar(255)                    NOT NULL,
    created_at              timestamp with time zone        NOT NULL,
    updated_at              timestamp with time zone        NOT NULL
);