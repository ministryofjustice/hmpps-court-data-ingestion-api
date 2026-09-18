ALTER TABLE court_next_hearing
ALTER COLUMN hearing_id TYPE uuid
USING hearing_id::uuid;