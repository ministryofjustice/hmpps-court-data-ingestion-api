ALTER TABLE court_charge
    ADD COLUMN code VARCHAR;

ALTER TABLE court_charge
    ADD COLUMN end_date DATE NULL;