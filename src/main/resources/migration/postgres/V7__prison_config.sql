CREATE TABLE prison_doc_notification_config
(
    prison_id              VARCHAR(6)                  NOT NULL CONSTRAINT prison_config_pk PRIMARY KEY,
    new_doc_date_from      TIMESTAMP WITH TIME ZONE    NOT NULL
);
