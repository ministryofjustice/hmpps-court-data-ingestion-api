ALTER TABLE court_next_hearing
    ADD COLUMN court_charge_result_id UUID NULL;

ALTER TABLE court_next_hearing
    ADD CONSTRAINT fk_court_next_hearing_charge_result
        FOREIGN KEY (court_charge_result_id)
            REFERENCES court_charge_result (id);


UPDATE court_next_hearing cnh
SET court_charge_result_id = ccr.id
    FROM court_charge_result ccr
JOIN court_charge cc
ON cc.id = ccr.court_charge_id
WHERE cc.court_hearing_id = cnh.court_hearing_id
  AND cc.defendant_id = cnh.defendant_id
  AND cc.master_defendant_id = cnh.master_defendant_id;


DELETE FROM court_next_hearing WHERE court_charge_result_id IS NULL;

ALTER TABLE court_next_hearing
    ALTER COLUMN court_charge_result_id SET NOT NULL;

ALTER TABLE court_next_hearing
    DROP COLUMN court_hearing_id;