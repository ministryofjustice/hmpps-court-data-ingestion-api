CREATE TABLE identified_warrant_file
(
    id                      UUID                            NOT NULL constraint identified_warrant_file_pk PRIMARY KEY,
    warrant_file_id         UUID                            NOT NULL,
    prisoner_number         varchar(255)                    NOT NULL,
    identified_at           timestamp with time zone        NOT NULL,

   CONSTRAINT fk_identified_warrant_file_warrant_file_id
       FOREIGN KEY (warrant_file_id) REFERENCES warrant_file(id)
);
