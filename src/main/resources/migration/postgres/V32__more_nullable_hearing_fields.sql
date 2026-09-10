ALTER TABLE court_charge
    ALTER COLUMN offence_legislation DROP NOT NULL;

ALTER TABLE court_charge
    ALTER COLUMN plea_date DROP NOT NULL;

ALTER TABLE court_charge
    ALTER COLUMN plea_value DROP NOT NULL;

ALTER TABLE court_next_hearing
    ALTER COLUMN hearing_id DROP NOT NULL;

ALTER TABLE court_next_hearing
    ALTER COLUMN date_time DROP NOT NULL;