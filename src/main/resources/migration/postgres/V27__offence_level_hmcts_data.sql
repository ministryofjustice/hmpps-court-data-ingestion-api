ALTER TABLE court_hearing
    ADD COLUMN api_response JSONB NULL;
ALTER TABLE court_hearing
    RENAME COLUMN court_id to hmcts_court_id;
ALTER TABLE court_hearing
    RENAME COLUMN court_code to hmpps_court_id;

CREATE TABLE court_charge
(
    id                  UUID PRIMARY KEY,
    court_hearing_id    UUID,
    defendant_id        UUID         NOT NULL,
    master_defendant_id UUID         NOT NULL,
    listing_number      INTEGER      NOT NULL,
    offence_legislation VARCHAR(255) NOT NULL,
    plea_date           DATE         NOT NULL,
    plea_value          VARCHAR(255) NOT NULL,
    start_date          DATE         NOT NULL,
    title               VARCHAR(255) NOT NULL,
    wording             VARCHAR(255) NOT NULL,

    CONSTRAINT fk_court_charge_hearing
        FOREIGN KEY (court_hearing_id)
            REFERENCES court_hearing (id)
);

CREATE TABLE court_charge_result
(
    id                 UUID PRIMARY KEY,
    court_charge_id    UUID,
    result_code        VARCHAR(255) NOT NULL,
    result_description VARCHAR(255) NOT NULL,

    CONSTRAINT fk_court_charge_result_charge
        FOREIGN KEY (court_charge_id)
            REFERENCES court_charge (id)
);

CREATE TABLE court_next_hearing
(
    id                  UUID PRIMARY KEY,
    court_hearing_id    UUID,
    defendant_id        UUID         NOT NULL,
    master_defendant_id UUID         NOT NULL,
    hmcts_court_id      UUID         NOT NULL,
    court_name          VARCHAR(255) NOT NULL,
    hmpps_court_id      VARCHAR(255),
    date_time           TIMESTAMP    NOT NULL,
    hearing_id          VARCHAR(255) NOT NULL,

    CONSTRAINT fk_court_next_hearing_hearing
        FOREIGN KEY (court_hearing_id)
            REFERENCES court_hearing (id)
);

CREATE INDEX idx_court_charge_court_hearing_id
    ON court_charge (court_hearing_id);

CREATE INDEX idx_court_charge_result_court_charge_id
    ON court_charge_result (court_charge_id);

CREATE INDEX idx_court_next_hearing_court_hearing_id
    ON court_next_hearing (court_hearing_id);