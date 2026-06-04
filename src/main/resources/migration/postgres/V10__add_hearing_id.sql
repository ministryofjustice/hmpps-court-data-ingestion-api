ALTER TABLE court_document
    ADD COLUMN IF NOT EXISTS court_hearing_id UUID NULL;