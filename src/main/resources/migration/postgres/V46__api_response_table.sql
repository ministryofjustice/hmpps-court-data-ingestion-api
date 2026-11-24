CREATE TABLE court_pcr_api_response (
    id                  UUID PRIMARY KEY,
    court_hearing_id UUID NOT NULL,
    defendant_id UUID NOT NULL,
    master_defendant_id UUID NOT NULL,
    api_response JSONB,

    CONSTRAINT fk_court_pcr_api_response_hearing
        FOREIGN KEY (court_hearing_id)
            REFERENCES court_hearing(id)
);


INSERT INTO court_pcr_api_response (
    id,
    court_hearing_id,
    defendant_id,
    master_defendant_id,
    api_response
)
SELECT DISTINCT ON (ch.id)
    gen_random_uuid(),
    ch.id,
    cc.defendant_id,
    cc.master_defendant_id,
    ch.api_response
FROM court_hearing ch
         JOIN court_charge cc
              ON cc.court_hearing_id = ch.id
WHERE ch.api_response IS NOT NULL;