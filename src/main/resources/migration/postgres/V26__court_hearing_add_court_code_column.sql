ALTER TABLE court_hearing
    ADD COLUMN court_code VARCHAR NULL;

COMMENT ON COLUMN court_hearing.court_code IS E'Description: The code of the court (could be considered as hmpps_court_id) where the appearance is taking place. These codes are held in Court Register. \nSource System: Court Register';