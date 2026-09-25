CREATE TABLE court_charge_result_text
(
    id                        UUID PRIMARY KEY,
    court_charge_result_id    UUID,
    key                       VARCHAR(255) NOT NULL,
    value                     TEXT NULL,

    CONSTRAINT fk_court_charge_result_text
        FOREIGN KEY (court_charge_result_id)
            REFERENCES court_charge_result (id)
);